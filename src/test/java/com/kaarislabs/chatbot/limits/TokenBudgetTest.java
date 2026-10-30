package com.kaarislabs.chatbot.limits;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

class TokenBudgetTest {

    /** A clock the test can move forward. */
    static final class MutableClock extends Clock {

        Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    @Test
    void exhaustedOnceTheDailyTotalIsReached() {
        TokenBudget budget = new TokenBudget(1_000, new MutableClock(Instant.parse("2026-10-30T06:00:00Z")));

        budget.record(600);
        assertThat(budget.exhausted()).isFalse();
        budget.record(400);
        assertThat(budget.exhausted()).isTrue();
        budget.record(50);
        assertThat(budget.usedToday()).isEqualTo(1_050);
    }

    @Test
    void resetsAtMidnightKolkataNotUtc() {
        // 18:29:59 UTC is 23:59:59 in Kolkata (UTC+05:30).
        MutableClock clock = new MutableClock(Instant.parse("2026-10-30T18:29:59Z"));
        TokenBudget budget = new TokenBudget(1_000, clock);
        budget.record(1_000);
        assertThat(budget.exhausted()).isTrue();

        clock.now = clock.now.plus(Duration.ofSeconds(1)); // midnight in Kolkata, still 30 Oct in UTC
        assertThat(budget.exhausted()).isFalse();
        assertThat(budget.usedToday()).isZero();
    }

    @Test
    void negativeOrMissingUsageCountsAsZero() {
        TokenBudget budget = new TokenBudget(10, new MutableClock(Instant.parse("2026-10-30T06:00:00Z")));
        budget.record(-5);
        assertThat(budget.usedToday()).isZero();
    }
}
