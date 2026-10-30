package com.kaarislabs.chatbot.chat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;

/**
 * What the tools returned in earlier turns of a session, so the output guard can accept a follow-up answered from
 * memory ("what is its purity?") when the fact did come from a tool. Same idle expiry and session cap as the chat
 * memory; only the last {@value #MAX_TURNS} turns that ran tools are kept.
 */
@Component
public class SessionEvidence {

    static final int MAX_TURNS = 10;

    private final Cache<String, List<List<Object>>> sessions;

    @Autowired
    public SessionEvidence(@Value("${kaaris.chat.session-idle-timeout}") Duration idleTimeout,
            @Value("${kaaris.chat.max-sessions}") long maxSessions) {
        this(idleTimeout, maxSessions, Ticker.systemTicker());
    }

    SessionEvidence(Duration idleTimeout, long maxSessions, Ticker ticker) {
        this.sessions = Caffeine.newBuilder()
                .expireAfterAccess(idleTimeout)
                .maximumSize(maxSessions)
                .ticker(ticker)
                .executor(Runnable::run)
                .build();
    }

    /** Tool results of earlier turns, oldest turn first; empty for an unknown or expired session. */
    public List<List<Object>> earlierTurns(String sessionId) {
        List<List<Object>> turns = sessionId == null ? null : sessions.getIfPresent(sessionId);
        return turns == null ? List.of() : turns;
    }

    /** Adds one turn's tool results; turns without results are not stored. */
    public void record(String sessionId, List<Object> toolResults) {
        if (sessionId == null || toolResults.isEmpty()) {
            return;
        }
        List<Object> turn = List.copyOf(toolResults);
        sessions.asMap().compute(sessionId, (id, turns) -> {
            List<List<Object>> updated = new ArrayList<>(turns == null ? List.of() : turns);
            updated.add(turn);
            while (updated.size() > MAX_TURNS) {
                updated.removeFirst();
            }
            return List.copyOf(updated);
        });
    }
}
