package com.kaarislabs.chatbot.limits;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Global daily cap on model tokens (input + output, as reported by the model). Once the day's total reaches the
 * budget no more model calls are made until midnight Asia/Kolkata. A turn already in progress may overshoot.
 */
@Component
public class TokenBudget {

    private static final Logger log = LoggerFactory.getLogger(TokenBudget.class);

    public static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");

    private final long dailyBudget;
    private final Clock clock;

    private LocalDate day;
    private long used;
    private boolean warned;

    @Autowired
    public TokenBudget(@Value("${kaaris.budget.daily-tokens}") long dailyBudget) {
        this(dailyBudget, Clock.system(ZONE));
    }

    TokenBudget(long dailyBudget, Clock clock) {
        this.dailyBudget = dailyBudget;
        this.clock = clock;
        this.day = today();
    }

    /** True when today's budget is used up; the model must not be called. */
    public synchronized boolean exhausted() {
        rollOver();
        return used >= dailyBudget;
    }

    public synchronized void record(long tokens) {
        rollOver();
        used += Math.max(0, tokens);
        if (used >= dailyBudget && !warned) {
            warned = true;
            log.warn("token_budget_exceeded used={} budget={}: model calls stop until midnight Asia/Kolkata", used,
                    dailyBudget);
        }
    }

    public synchronized long usedToday() {
        rollOver();
        return used;
    }

    /** Today's date in Kolkata, whatever zone the clock is in. */
    private LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), ZONE);
    }

    private void rollOver() {
        LocalDate today = today();
        if (!today.equals(day)) {
            day = today;
            used = 0;
            warned = false;
        }
    }
}
