package com.kaarislabs.chatbot.limits;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

import io.github.bucket4j.TimeMeter;

class MessageRateLimiterTest {

    final AtomicLong nanos = new AtomicLong();
    final TimeMeter time = new TimeMeter() {
        @Override
        public long currentTimeNanos() {
            return nanos.get();
        }

        @Override
        public boolean isWallClockBased() {
            return false;
        }
    };
    final MessageRateLimiter limiter = new MessageRateLimiter(10, 100, time, nanos::get);

    private void advance(Duration d) {
        nanos.addAndGet(d.toNanos());
    }

    @Test
    void eleventhMessageInAMinuteIsRefusedEvenWhenSpreadOut() {
        // 11 messages 5 seconds apart all fall within one minute; a greedy refill would have let the 11th through.
        for (int i = 0; i < 10; i++) {
            assertThat(limiter.tryAcquire("8.8.8.8")).as("message %d", i + 1).isTrue();
            advance(Duration.ofSeconds(5));
        }
        assertThat(limiter.tryAcquire("8.8.8.8")).isFalse();
        assertThat(limiter.tryAcquire("1.1.1.1")).isTrue();

        advance(Duration.ofSeconds(10)); // a minute after the first message
        assertThat(limiter.tryAcquire("8.8.8.8")).isTrue();
    }

    @Test
    void dailyLimitHoldsEvenWhenPacedUnderTheMinuteLimit() {
        for (int i = 0; i < 100; i++) {
            assertThat(limiter.tryAcquire("8.8.8.8")).isTrue();
            advance(Duration.ofSeconds(7));
        }
        assertThat(limiter.tryAcquire("8.8.8.8")).isFalse();

        advance(Duration.ofHours(24));
        assertThat(limiter.tryAcquire("8.8.8.8")).isTrue();
    }
}
