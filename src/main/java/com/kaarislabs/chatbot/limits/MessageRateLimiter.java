package com.kaarislabs.chatbot.limits;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;

import io.github.bucket4j.Bucket;
import io.github.bucket4j.TimeMeter;

/**
 * Per-visitor message limits (in memory, so per instance): a short burst limit and a daily limit. Buckets of
 * visitors idle for a day are dropped, and at most {@value #MAX_KEYS} are kept.
 */
@Component
public class MessageRateLimiter {

    static final long MAX_KEYS = 100_000;
    static final Duration IDLE_EXPIRY = Duration.ofDays(1);

    private final long perMinute;
    private final long perDay;
    private final TimeMeter time;
    private final Cache<String, Bucket> buckets;

    @Autowired
    public MessageRateLimiter(@Value("${kaaris.rate-limit.per-minute}") long perMinute,
            @Value("${kaaris.rate-limit.per-day}") long perDay) {
        this(perMinute, perDay, TimeMeter.SYSTEM_NANOTIME, Ticker.systemTicker());
    }

    MessageRateLimiter(long perMinute, long perDay, TimeMeter time, Ticker ticker) {
        this.perMinute = perMinute;
        this.perDay = perDay;
        this.time = time;
        this.buckets = Caffeine.newBuilder()
                .maximumSize(MAX_KEYS)
                .expireAfterAccess(IDLE_EXPIRY)
                .ticker(ticker)
                .build();
    }

    /** @return true if this visitor may send another message now (and counts it) */
    public boolean tryAcquire(String visitorKey) {
        return buckets.get(visitorKey, k -> newBucket()).tryConsume(1);
    }

    /**
     * Interval refills: the whole allowance comes back at once at the end of each period. A greedy refill would add
     * one message every 6 seconds, so a visitor typing steadily would never reach "10 in a minute".
     */
    private Bucket newBucket() {
        return Bucket.builder()
                .addLimit(limit -> limit.capacity(perMinute).refillIntervally(perMinute, Duration.ofMinutes(1)))
                .addLimit(limit -> limit.capacity(perDay).refillIntervally(perDay, Duration.ofDays(1)))
                .withCustomTimePrecision(time)
                .build();
    }
}
