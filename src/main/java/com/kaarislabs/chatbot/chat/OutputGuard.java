package com.kaarislabs.chatbot.chat;

import java.util.Optional;

/**
 * Last check on a reply before the customer sees any of it. Runs on the complete reply text after all tools have run,
 * so it can compare the reply with what the tools returned (via {@link ChatContext#toolResults()}).
 */
@FunctionalInterface
public interface OutputGuard {

    /**
     * @param userMessage the customer's message of this turn
     * @return the first broken rule, or empty when the reply may be sent
     */
    Optional<Violation> check(String reply, String userMessage, ChatContext context);

    /**
     * @param rule  short machine-readable rule name, e.g. "unknown_price"
     * @param token the offending part of the reply (never the whole reply)
     */
    record Violation(String rule, String token) {
    }
}
