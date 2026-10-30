package com.kaarislabs.chatbot.chat;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

class ChatServiceTest {

    @Test
    void replyJoinsEveryTextPart() {
        ChatResponse response = new ChatResponse(List.of(
                new Generation(new AssistantMessage("Yes, 2 products match:\n1. BA044\n")),
                new Generation(new AssistantMessage("2. KL-05-03243\n"))));

        assertThat(ChatService.text(response)).isEqualTo("Yes, 2 products match:\n1. BA044\n2. KL-05-03243");
    }

    @Test
    void emptyOrMissingReplyFallsBack() {
        assertThat(ChatService.text(null)).isEqualTo(ChatService.FALLBACK_REPLY);
        assertThat(ChatService.text(new ChatResponse(List.of()))).isEqualTo(ChatService.FALLBACK_REPLY);
        assertThat(ChatService.text(new ChatResponse(List.of(new Generation(new AssistantMessage("  "))))))
                .isEqualTo(ChatService.FALLBACK_REPLY);
    }
}
