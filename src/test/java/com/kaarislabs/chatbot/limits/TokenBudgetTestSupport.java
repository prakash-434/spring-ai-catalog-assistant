package com.kaarislabs.chatbot.limits;

import java.time.Clock;

/** Lets tests in other packages build a {@link TokenBudget} on a fixed clock. */
public final class TokenBudgetTestSupport {

    private TokenBudgetTestSupport() {
    }

    public static TokenBudget budget(long dailyTokens, Clock clock) {
        return new TokenBudget(dailyTokens, clock);
    }
}
