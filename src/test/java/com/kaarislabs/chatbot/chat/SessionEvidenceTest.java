package com.kaarislabs.chatbot.chat;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

class SessionEvidenceTest {

    final AtomicLong nanos = new AtomicLong();
    final SessionEvidence evidence = new SessionEvidence(Duration.ofMinutes(30), 100, nanos::get);

    @Test
    void keepsOnlyTheLastTenToolTurnsOldestFirst() {
        for (int i = 1; i <= 12; i++) {
            evidence.record("s1", List.of("turn " + i));
        }
        evidence.record("s1", List.of()); // a turn without tools is not stored

        List<List<Object>> turns = evidence.earlierTurns("s1");
        assertThat(turns).hasSize(SessionEvidence.MAX_TURNS);
        assertThat(turns.getFirst()).containsExactly("turn 3");
        assertThat(turns.getLast()).containsExactly("turn 12");
    }

    @Test
    void expiresThirtyMinutesAfterLastAccess() {
        evidence.record("s1", List.of("turn 1"));

        nanos.addAndGet(Duration.ofMinutes(29).toNanos());
        assertThat(evidence.earlierTurns("s1")).hasSize(1);

        nanos.addAndGet(Duration.ofMinutes(31).toNanos());
        assertThat(evidence.earlierTurns("s1")).isEmpty();
    }

    @Test
    void sessionsAreSeparate() {
        evidence.record("s1", List.of("a"));

        assertThat(evidence.earlierTurns("s2")).isEmpty();
        assertThat(evidence.earlierTurns(null)).isEmpty();
    }
}
