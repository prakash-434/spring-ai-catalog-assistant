package com.kaarislabs.chatbot.chat;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

class ConversationMemoryTest {

    final ChatMemory memory = MessageWindowChatMemory.builder()
            .chatMemoryRepository(new InMemoryChatMemoryRepository())
            .maxMessages(10)
            .build();
    final ConversationMemory conversation = new ConversationMemory(memory);

    @Test
    void lastReplyIsReplacedEvenWhenStoredInSeveralParts() {
        memory.add("s1", List.of(new UserMessage("Do you have BA044?"), new AssistantMessage("Yes, in stock.")));
        memory.add("s1", List.of(new UserMessage("price?"), new AssistantMessage("It costs "),
                new AssistantMessage("$99.00.")));

        conversation.replaceLastReply("s1", "Salbutamol-D9 acetate (Cat No BA044)\nPlease request a quote.");

        List<Message> kept = memory.get("s1");
        assertThat(kept).extracting(Message::getText).containsExactly(
                "Do you have BA044?", "Yes, in stock.", "price?",
                "Salbutamol-D9 acetate (Cat No BA044)\nPlease request a quote.");
        assertThat(kept.getLast()).isInstanceOf(AssistantMessage.class);
    }

    @Test
    void otherSessionsAndEmptySessionsAreLeftAlone() {
        memory.add("other", List.of(new UserMessage("hi"), new AssistantMessage("hello")));

        conversation.replaceLastReply("s1", "fallback");

        assertThat(memory.get("s1")).isEmpty();
        assertThat(memory.get("other")).extracting(Message::getText).containsExactly("hi", "hello");
    }
}
