package com.kaarislabs.chatbot.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.http.MediaType;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.kaarislabs.chatbot.fallback.FallbackResponder;
import com.kaarislabs.chatbot.geo.CountryResolver;
import com.kaarislabs.chatbot.limits.MessageRateLimiter;
import com.kaarislabs.chatbot.limits.TokenBudgetTestSupport;
import com.kaarislabs.chatbot.security.InternalTokenFilter;
import com.kaarislabs.chatbot.stats.ChatStats;

/**
 * POST /chat end to end except the model: real stream service, context factory, token filter, rate limiter and SSE
 * writing; the chat service (the model) and the fallback are mocked, and the turn runs on the calling thread so the
 * stream is complete when perform() returns.
 */
class ChatControllerTest {

    static final String SESSION = "3f1c2a9e-8b7d-4c6e-9a1b-2d3e4f5a6b7c";
    static final String TOKEN = "test-token";

    final ChatService chatService = mock(ChatService.class);
    final FallbackResponder fallback = mock(FallbackResponder.class);
    final ChatStats stats = new ChatStats(new BigDecimal("1.50"), new BigDecimal("9.00"));

    private MockMvc mvc(String... profiles) {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles(profiles);
        CountryResolver countries = new CountryResolver(ip -> Optional.of("US"));
        ChatStreamService streams = new ChatStreamService(chatService, (reply, message, ctx) -> Optional.empty(),
                e -> false, TokenBudgetTestSupport.budget(1_000_000,
                        Clock.fixed(Instant.parse("2026-10-30T06:00:00Z"), ZoneOffset.UTC)),
                fallback, stats, new SessionEvidence(Duration.ofMinutes(30), 100),
                new ConversationMemory(MessageWindowChatMemory.builder()
                        .chatMemoryRepository(new InMemoryChatMemoryRepository()).maxMessages(10).build()));
        ChatController controller = new ChatController(streams, new ChatContextFactory(countries, env),
                new MessageRateLimiter(10, 100), stats, Runnable::run);
        return MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ChatExceptionHandler(e -> false))
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
        return stream(mvc, request, 200);
    }

    private static String stream(MockMvc mvc, MockHttpServletRequestBuilder request, int expectedStatus)
            throws Exception {
        MvcResult result = mvc.perform(request).andExpect(request().asyncStarted()).andReturn();
        mvc.perform(asyncDispatch(result)).andExpect(status().is(expectedStatus));
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
            return new ChatReply("BA072 costs $345.00 for 5 mg in USD.", ctx.actions(), ctx.toolsCalled(), 9,
                    ChatReply.LLM);
        });

        String body = stream(mvc(), chat(SESSION, "What's the price of BA072?"));

        assertThat(eventNames(body)).containsExactly(
                "status", "status", "token", "token", "action", "action", "done");
        assertThat(body).contains("Searching products…", "Checking pack sizes…",
                "{\"text\":\"BA072 costs $345.00 \"}", "{\"text\":\"for 5 mg in USD.\"}",
                "{\"type\":\"product\",\"catNo\":\"BA072\"}", "{\"type\":\"quote\",\"catNo\":\"BA072\"}",
                "{\"latencyMs\":9,\"mode\":\"llm\"}");
    }

    @Test
    void modelFailureStreamsTheFallbackMarkedAsSuch() throws Exception {
        when(chatService.chat(any(), anyString())).thenThrow(new IllegalStateException("model unavailable"));
        when(fallback.answer("Do you have BA044?", "IN")).thenReturn(new FallbackResponder.Answer(
                "Salbutamol-D9 acetate (Cat No BA044)", List.of(ChatAction.product("BA044"))));

        String body = stream(mvc(), chat(SESSION, "Do you have BA044?"));

        assertThat(eventNames(body)).containsExactly("token", "token", "action", "done");
        assertThat(body).contains("\"mode\":\"fallback\"").doesNotContain("model unavailable");
    }

    @Test
    void failingFallbackIsOneFriendlyErrorEvent() throws Exception {
        when(chatService.chat(any(), anyString())).thenThrow(new IllegalStateException("model unavailable"));
        when(fallback.answer(anyString(), anyString())).thenThrow(new IllegalStateException(
                "Connection to 10.1.2.3:1433 refused for user kaaris_chatbot"));

        String body = stream(mvc(), chat(SESSION, "hi"));

        assertThat(eventNames(body)).containsExactly("error");
        assertThat(body).contains(ChatStreamService.FAILED)
                .doesNotContain("10.1.2.3", "1433", "refused", "kaaris_chatbot", "IllegalStateException");
    }

    @Test
    void eleventhMessageInAMinuteIs429WithoutAModelCall() throws Exception {
        when(chatService.chat(any(), anyString()))
                .thenReturn(new ChatReply("ok", List.of(), List.of(), 1, ChatReply.LLM));
        MockMvc mvc = mvc();
        for (int i = 0; i < 10; i++) {
            stream(mvc, chat(SESSION, "q" + i).header(ChatContextFactory.CLIENT_IP_HEADER, "8.8.8.8"));
        }

        String body = stream(mvc, chat(SESSION, "q10").header(ChatContextFactory.CLIENT_IP_HEADER, "8.8.8.8"), 429);

        assertThat(eventNames(body)).containsExactly("error");
        assertThat(body).contains(ChatStreamService.VISITOR_RATE_LIMITED);
        verify(chatService, times(10)).chat(any(), anyString());
        verify(chatService, never()).chat(any(), eq("q10"));
        assertThat(stats.snapshotAndReset("test")).contains("messages=11", "rateLimited=1");

        // Another visitor is not affected.
        stream(mvc, chat(SESSION, "other").header(ChatContextFactory.CLIENT_IP_HEADER, "1.1.1.1"));
    }

    @Test
    void missingOrWrongTokenIs401() throws Exception {
        MockMvc mvc = mvc();
        mvc.perform(chatWithToken(SESSION, "hi", "wrong")).andExpect(status().isUnauthorized());
        mvc.perform(chatWithToken(SESSION, "hi", null)).andExpect(status().isUnauthorized());
        verify(chatService, never()).chat(any(), anyString());
    }

    @Test
    void invalidRequestsAre400Json() throws Exception {
        MockMvc mvc = mvc();
        mvc.perform(chat("not-a-uuid", "hi"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value("sessionId must be a UUID."));
        mvc.perform(chat(SESSION, "   "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Please enter a message."));
        mvc.perform(chat(SESSION, "\\u0000\\u200B\\t"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Please enter a message."));
        mvc.perform(chat(SESSION, "x".repeat(501)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("A message can be at most 500 characters."));
        verify(chatService, never()).chat(any(), anyString());
    }

    @Test
    void messageIsCleanedBeforeTheTurn() throws Exception {
        when(chatService.chat(any(), anyString()))
                .thenReturn(new ChatReply("ok", List.of(), List.of(), 1, ChatReply.LLM));

        // Control characters, repeated whitespace and padding are removed before the turn.
        stream(mvc(), chat(SESSION, "  Do   you\\u0000 have\\t BA044? " + " ".repeat(20)));

        verify(chatService).chat(any(), eq("Do you have BA044?"));
    }

    @Test
    void clientIpIsUsedOnlyBehindAValidToken() throws Exception {
        when(chatService.chat(any(), anyString()))
                .thenReturn(new ChatReply("ok", List.of(), List.of(), 1, ChatReply.LLM));
        ArgumentCaptor<ChatContext> captor = ArgumentCaptor.forClass(ChatContext.class);

        stream(mvc(), chat(SESSION, "price").header(ChatContextFactory.CLIENT_IP_HEADER, "8.8.8.8"));

        verify(chatService).chat(captor.capture(), eq("price"));
        assertThat(captor.getValue().countryCode()).isEqualTo("US");
        assertThat(captor.getValue().sessionId()).isEqualTo(SESSION);
    }

    @Test
    void debugCountryOverridesOnlyInDev() throws Exception {
        when(chatService.chat(any(), anyString()))
                .thenReturn(new ChatReply("ok", List.of(), List.of(), 1, ChatReply.LLM));
        ArgumentCaptor<ChatContext> captor = ArgumentCaptor.forClass(ChatContext.class);

        stream(mvc(), chat(SESSION, "a").header(ChatContextFactory.DEBUG_COUNTRY_HEADER, "gb"));
        stream(mvc("dev"), chat(SESSION, "b").header(ChatContextFactory.DEBUG_COUNTRY_HEADER, "gb")
                .header(ChatContextFactory.CLIENT_IP_HEADER, "8.8.8.8"));

        verify(chatService).chat(captor.capture(), eq("a"));
        verify(chatService).chat(captor.capture(), eq("b"));
        assertThat(captor.getAllValues()).extracting(ChatContext::countryCode).containsExactly("IN", "GB");
    }
}
