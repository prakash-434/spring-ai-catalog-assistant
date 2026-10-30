package com.kaarislabs.chatbot.stats;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

import jakarta.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Daily counters, logged as one INFO line at midnight Asia/Kolkata and on shutdown. Counts only; no message text,
 * session ids or IPs.
 */
@Component
public class ChatStats {

    private static final Logger log = LoggerFactory.getLogger(ChatStats.class);
    private static final BigDecimal MILLION = BigDecimal.valueOf(1_000_000);

    private final BigDecimal inputPricePerMillion;
    private final BigDecimal outputPricePerMillion;

    private final LongAdder messages = new LongAdder();
    private final LongAdder llmTurns = new LongAdder();
    private final Map<String, LongAdder> fallbacks = new ConcurrentHashMap<>();
    private final LongAdder rateLimited = new LongAdder();
    private final LongAdder guardViolations = new LongAdder();
    private final LongAdder inputTokens = new LongAdder();
    private final LongAdder outputTokens = new LongAdder();
    private final LongAdder zeroResultSearches = new LongAdder();

    public ChatStats(@Value("${kaaris.budget.input-price-per-million}") BigDecimal inputPricePerMillion,
            @Value("${kaaris.budget.output-price-per-million}") BigDecimal outputPricePerMillion) {
        this.inputPricePerMillion = inputPricePerMillion;
        this.outputPricePerMillion = outputPricePerMillion;
    }

    public void message() {
        messages.increment();
    }

    public void llmTurn(long input, long output) {
        llmTurns.increment();
        inputTokens.add(input);
        outputTokens.add(output);
    }

    public void fallback(String reason) {
        fallbacks.computeIfAbsent(reason, r -> new LongAdder()).increment();
    }

    public void rateLimited() {
        rateLimited.increment();
    }

    public void guardViolation() {
        guardViolations.increment();
    }

    public void zeroResultSearch() {
        zeroResultSearches.increment();
    }

    /** Estimated model cost in USD for the given token counts, for logging only. */
    public BigDecimal estimatedCost(long input, long output) {
        return BigDecimal.valueOf(input).multiply(inputPricePerMillion)
                .add(BigDecimal.valueOf(output).multiply(outputPricePerMillion))
                .divide(MILLION, 4, RoundingMode.HALF_UP);
    }

    @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Kolkata")
    public void logDaily() {
        log.info(snapshotAndReset("daily"));
    }

    @PreDestroy
    public void logOnShutdown() {
        log.info(snapshotAndReset("shutdown"));
    }

    /** The stats line; counters restart from zero. Counts arriving during the reset may land in either day. */
    public String snapshotAndReset(String trigger) {
        Map<String, Long> fallbackCounts = new TreeMap<>();
        fallbacks.forEach((reason, count) -> fallbackCounts.put(reason, count.sumThenReset()));
        long fallbackTotal = fallbackCounts.values().stream().mapToLong(Long::longValue).sum();
        long input = inputTokens.sumThenReset();
        long output = outputTokens.sumThenReset();
        return "chat_daily_stats trigger=" + trigger
                + " messages=" + messages.sumThenReset()
                + " llmTurns=" + llmTurns.sumThenReset()
                + " fallbackTurns=" + fallbackTotal + " fallbackByReason=" + fallbackCounts
                + " rateLimited=" + rateLimited.sumThenReset()
                + " guardViolations=" + guardViolations.sumThenReset()
                + " inputTokens=" + input + " outputTokens=" + output + " totalTokens=" + (input + output)
                + " estimatedCostUsd=" + estimatedCost(input, output)
                + " zeroResultSearches=" + zeroResultSearches.sumThenReset();
    }
}
