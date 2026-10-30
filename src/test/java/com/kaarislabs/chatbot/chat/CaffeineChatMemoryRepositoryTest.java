package com.kaarislabs.chatbot.chat;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

import com.github.benmanes.caffeine.cache.Ticker;

class CaffeineChatMemoryRepositoryTest {

    static final Duration IDLE = Duration.ofMinutes(30);

    final AtomicLong nanos = new AtomicLong();
    final Ticker ticker = nanos::get;

    private void advance(Duration d) {
        nanos.addAndGet(d.toNanos());
    }

    private CaffeineChatMemoryRepository repository(long maxSessions) {
        return new CaffeineChatMemoryRepository(IDLE, maxSessions, ticker, Runnable::run);
    }

    @Test
    void sessionExpires30MinutesAfterLastAccess() {
        CaffeineChatMemoryRepository repo = repository(10);
        repo.saveAll("a", List.of(new UserMessage("hi")));

        advance(Duration.ofMinutes(29));
        assertThat(repo.findByConversationId("a")).hasSize(1); // a read counts as access

        advance(Duration.ofMinutes(29));
        assertThat(repo.findByConversationId("a")).hasSize(1);

        advance(Duration.ofMinutes(31));
        assertThat(repo.findByConversationId("a")).isEmpty();
    }

    @Test
    void oldestSessionsAreDroppedAboveTheMaximum() {
        CaffeineChatMemoryRepository repo = repository(3);
        for (int i = 0; i < 10; i++) {
            repo.saveAll("s" + i, List.of(new UserMessage("m" + i)));
        }
        repo.cleanUp();

        assertThat(repo.size()).isLessThanOrEqualTo(3);
        assertThat(repo.findByConversationId("s9")).hasSize(1);
    }

    @Test
    void memoryKeepsTheLastTenMessagesPerSession() {
        ChatMemory memory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(repository(10))
                .maxMessages(10)
                .build();
        for (int i = 1; i <= 8; i++) {
            memory.add("a", List.of(new UserMessage("q" + i), new AssistantMessage("a" + i)));
        }
        memory.add("b", new UserMessage("other"));

        List<Message> kept = memory.get("a");
        assertThat(kept).hasSize(10);
        assertThat(kept.getFirst().getText()).isEqualTo("q4");
        assertThat(kept.getLast().getText()).isEqualTo("a8");
        assertThat(memory.get("b")).extracting(Message::getText).containsExactly("other");
    }
}
