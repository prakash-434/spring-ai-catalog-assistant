package com.kaarislabs.chatbot.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** Request validation and the dev-only country header; the chat service (and model) is mocked. */
class ChatControllerTest {

    final ChatService chatService = mock(ChatService.class);
    final ChatContext context = new ChatContext();

    private MockMvc mvc(String... profiles) {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles(profiles);
        return MockMvcBuilders.standaloneSetup(new ChatController(chatService, context, env))
                .setControllerAdvice(new ChatExceptionHandler(e -> false))
                .build();
    }

    private static String body(String sessionId, String message) {
        return "{\"sessionId\":\"" + sessionId + "\",\"message\":\"" + message + "\"}";
    }

    @Test
    void validMessageIsAnswered() throws Exception {
        when(chatService.chat("Do you have BA044?")).thenReturn(
                new ChatReply("Yes.", List.of(ChatAction.product("BA044")), List.of("searchProducts"), 5));

        mvc().perform(post("/chat").contentType(MediaType.APPLICATION_JSON)
                        .content(body("s1", "  Do you have BA044? ")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").value("Yes."))
                .andExpect(jsonPath("$.actions[0].type").value("product"))
                .andExpect(jsonPath("$.actions[0].catNo").value("BA044"))
                .andExpect(jsonPath("$.actions[0].path").doesNotExist())
                .andExpect(jsonPath("$.toolsCalled[0]").value("searchProducts"));
        assertThat(context.sessionId()).isEqualTo("s1");
        assertThat(context.countryCode()).isEqualTo("IN");
    }

    @Test
    void emptyMessageIs400() throws Exception {
        mvc().perform(post("/chat").contentType(MediaType.APPLICATION_JSON).content(body("s1", "   ")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Please enter a message."));
        verify(chatService, never()).chat(anyString());
    }

    @Test
    void messageOver500CharactersIs400() throws Exception {
        mvc().perform(post("/chat").contentType(MediaType.APPLICATION_JSON).content(body("s1", "x".repeat(501))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("A message can be at most 500 characters."));
        verify(chatService, never()).chat(anyString());
    }

    @Test
    void missingOrUnsafeSessionIdIs400() throws Exception {
        mvc().perform(post("/chat").contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"hi\"}"))
                .andExpect(status().isBadRequest());
        mvc().perform(post("/chat").contentType(MediaType.APPLICATION_JSON).content(body("a b'", "hi")))
                .andExpect(status().isBadRequest());
        verify(chatService, never()).chat(anyString());
    }

    @Test
    void debugCountryHeaderWorksOnlyInDevProfile() throws Exception {
        mvc().perform(post("/chat").contentType(MediaType.APPLICATION_JSON).content(body("s1", "price"))
                .header(ChatController.DEBUG_COUNTRY_HEADER, "us"));
        assertThat(context.countryCode()).isEqualTo("IN");

        mvc("dev").perform(post("/chat").contentType(MediaType.APPLICATION_JSON).content(body("s1", "price"))
                .header(ChatController.DEBUG_COUNTRY_HEADER, "us"));
        assertThat(context.countryCode()).isEqualTo("US");
    }

    @Test
    void rateLimitedModelIs429() throws Exception {
        when(chatService.chat("hi")).thenThrow(new IllegalStateException("quota"));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new ChatController(chatService, context, new MockEnvironment()))
                .setControllerAdvice(new ChatExceptionHandler(e -> true))
                .build();

        mvc.perform(post("/chat").contentType(MediaType.APPLICATION_JSON).content(body("s1", "hi")))
                .andExpect(status().isTooManyRequests());
    }
}
