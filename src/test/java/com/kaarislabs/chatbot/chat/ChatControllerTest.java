package com.kaarislabs.chatbot.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.kaarislabs.chatbot.geo.CountryResolver;
import com.kaarislabs.chatbot.security.InternalTokenFilter;

/**
 * POST /chat end to end except the model: real stream service, context factory, token filter and SSE writing; the
 * chat service is mocked and the turn runs on the calling thread so the stream is complete when perform() returns.
 */
class ChatControllerTest {

    static final String SESSION = "3f1c2a9e-8b7d-4c6e-9a1b-2d3e4f5a6b7c";
    static final String TOKEN = "test-token";

    final ChatService chatService = mock(ChatService.class);

    private MockMvc mvc(boolean rateLimited, String... profiles) {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles(profiles);
        CountryResolver countries = new CountryResolver(ip -> Optional.of("US"));
        ChatStreamService streams = new ChatStreamService(chatService, (reply, ctx) -> reply, e -> rateLimited);
        return MockMvcBuilders
                .standaloneSetup(new ChatController(streams, new ChatContextFactory(countries, env), Runnable::run))
                .setControllerAdvice(new ChatExceptionHandler(e -> rateLimited))
                .addFilters(new InternalTokenFilter(TOKEN, env))
                .build();
    }

    private static MockHttpServletRequestBuilder chat(String sessionId, String message) {
        return chatWithToken(sessionId, message, TOKEN);
    }

    private static MockHttpServletRequestBuilder chatWithToken(String sessionId, String message, String token) {
        MockHttpServletRequestBuilder builder = post("/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM, MediaType.APPLICATION_JSON)
                .content("{\"sessionId\":\"" + sessionId + "\",\"message\":\"" + message + "\"}");
        return token == null ? builder : builder.header(InternalTokenFilter.HEADER, token);
    }

    private static String stream(MockMvc mvc, MockHttpServletRequestBuilder request) throws Exception {
        MvcResult result = mvc.perform(request).andExpect(request().asyncStarted()).andReturn();
        mvc.perform(asyncDispatch(result)).andExpect(status().isOk());
        assertThat(result.getResponse().getContentType()).startsWith(MediaType.TEXT_EVENT_STREAM_VALUE);
        // SSE is always UTF-8 (the spec allows nothing else); the mock response would default to ISO-8859-1.
        return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private static List<String> eventNames(String body) {
        return Arrays.stream(body.split("\n"))
                .filter(l -> l.startsWith("event:"))
                .map(l -> l.substring("event:".length()))
                .toList();
    }

    @Test
    void eventsComeInOrderStatusTokenActionDone() throws Exception {
        when(chatService.chat(any(), eq("What's the price of BA072?"))).thenAnswer(inv -> {
            ChatContext ctx = inv.getArgument(0);
            ctx.beginToolCall("searchProducts");
            ctx.beginToolCall("getPackSizesAndPrice");
            ctx.addAction(ChatAction.product("BA072"));
            ctx.addAction(ChatAction.quote("BA072"));
            ctx.addAction(ChatAction.product("BA072"));
            return new ChatReply("BA072 costs $345.00 for 5 mg in USD.", ctx.actions(), ctx.toolsCalled(), 9);
        });

        String body = stream(mvc(false), chat(SESSION, "What's the price of BA072?"));

        assertThat(eventNames(body)).containsExactly(
                "status", "status", "token", "token", "action", "action", "done");
        assertThat(body).contains("Searching products…", "Checking pack sizes…",
                "{\"text\":\"BA072 costs $345.00 \"}", "{\"text\":\"for 5 mg in USD.\"}",
                "{\"type\":\"product\",\"catNo\":\"BA072\"}", "{\"type\":\"quote\",\"catNo\":\"BA072\"}",
                "{\"latencyMs\":9}");
    }

    @Test
    void modelFailureBecomesOneFriendlyErrorEvent() throws Exception {
        when(chatService.chat(any(), anyString())).thenThrow(new IllegalStateException(
                "Connection to 10.1.2.3:1433 refused for user kaaris_chatbot"));

        String body = stream(mvc(false), chat(SESSION, "hi"));

        assertThat(eventNames(body)).containsExactly("error");
        assertThat(body).contains(ChatStreamService.FAILED)
                .doesNotContain("10.1.2.3", "1433", "refused", "kaaris_chatbot", "IllegalStateException");
    }

    @Test
    void rateLimitHasItsOwnMessage() throws Exception {
        when(chatService.chat(any(), anyString())).thenThrow(new IllegalStateException("429 RESOURCE_EXHAUSTED"));

        String body = stream(mvc(true), chat(SESSION, "hi"));

        assertThat(eventNames(body)).containsExactly("error");
        assertThat(body).contains(ChatStreamService.RATE_LIMITED).doesNotContain("RESOURCE_EXHAUSTED");
    }

    @Test
    void missingOrWrongTokenIs401() throws Exception {
        MockMvc mvc = mvc(false);
        mvc.perform(chatWithToken(SESSION, "hi", "wrong")).andExpect(status().isUnauthorized());
        mvc.perform(chatWithToken(SESSION, "hi", null)).andExpect(status().isUnauthorized());
        verify(chatService, never()).chat(any(), anyString());
    }

    @Test
    void invalidRequestsAre400Json() throws Exception {
        MockMvc mvc = mvc(false);
        mvc.perform(chat("not-a-uuid", "hi"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value("sessionId must be a UUID."));
        mvc.perform(chat(SESSION, "   "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Please enter a message."));
        mvc.perform(chat(SESSION, "x".repeat(501)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("A message can be at most 500 characters."));
        verify(chatService, never()).chat(any(), anyString());
    }

    @Test
    void clientIpIsUsedOnlyBehindAValidToken() throws Exception {
        when(chatService.chat(any(), anyString())).thenReturn(new ChatReply("ok", List.of(), List.of(), 1));
        ArgumentCaptor<ChatContext> captor = ArgumentCaptor.forClass(ChatContext.class);

        stream(mvc(false), chat(SESSION, "price").header(ChatContextFactory.CLIENT_IP_HEADER, "8.8.8.8"));

        verify(chatService).chat(captor.capture(), eq("price"));
        assertThat(captor.getValue().countryCode()).isEqualTo("US");
        assertThat(captor.getValue().sessionId()).isEqualTo(SESSION);
    }

    @Test
    void debugCountryOverridesOnlyInDev() throws Exception {
        when(chatService.chat(any(), anyString())).thenReturn(new ChatReply("ok", List.of(), List.of(), 1));
        ArgumentCaptor<ChatContext> captor = ArgumentCaptor.forClass(ChatContext.class);

        stream(mvc(false), chat(SESSION, "a").header(ChatContextFactory.DEBUG_COUNTRY_HEADER, "gb"));
        stream(mvc(false, "dev"), chat(SESSION, "b").header(ChatContextFactory.DEBUG_COUNTRY_HEADER, "gb")
                .header(ChatContextFactory.CLIENT_IP_HEADER, "8.8.8.8"));

        verify(chatService).chat(captor.capture(), eq("a"));
        verify(chatService).chat(captor.capture(), eq("b"));
        assertThat(captor.getAllValues()).extracting(ChatContext::countryCode).containsExactly("IN", "GB");
    }
}
