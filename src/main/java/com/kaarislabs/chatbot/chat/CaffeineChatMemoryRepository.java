package com.kaarislabs.chatbot.chat;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executor;

import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.Message;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;

/**
 * Chat memory kept in memory with a bound: a session is dropped after a period without reads or writes, and the
 * least recently used sessions go first once the maximum is reached. The message window is applied by the
 * {@link org.springframework.ai.chat.memory.ChatMemory} on top.
 */
public class CaffeineChatMemoryRepository implements ChatMemoryRepository {

    private final Cache<String, List<Message>> sessions;

    public CaffeineChatMemoryRepository(Duration idleTimeout, long maxSessions) {
        this(idleTimeout, maxSessions, Ticker.systemTicker(), Runnable::run);
    }

    /** @param executor runs Caffeine's maintenance; a direct executor makes eviction deterministic in tests */
    CaffeineChatMemoryRepository(Duration idleTimeout, long maxSessions, Ticker ticker, Executor executor) {
        this.sessions = Caffeine.newBuilder()
                .expireAfterAccess(idleTimeout)
                .maximumSize(maxSessions)
                .ticker(ticker)
                .executor(executor)
                .build();
    }

    @Override
    public List<String> findConversationIds() {
        return List.copyOf(sessions.asMap().keySet());
    }

    @Override
    public List<Message> findByConversationId(String conversationId) {
        List<Message> messages = sessions.getIfPresent(conversationId);
        return messages == null ? List.of() : messages;
    }

    @Override
    public void saveAll(String conversationId, List<Message> messages) {
        sessions.put(conversationId, List.copyOf(messages));
    }

    @Override
    public void deleteByConversationId(String conversationId) {
        sessions.invalidate(conversationId);
    }

    /** For tests: runs pending evictions now. */
    void cleanUp() {
        sessions.cleanUp();
    }

    long size() {
        return sessions.estimatedSize();
    }
}
