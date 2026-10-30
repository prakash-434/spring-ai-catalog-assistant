package com.kaarislabs.chatbot.eval;

import java.util.List;

/** What the runner records; one {@link CaseResult} per line of results-&lt;model&gt;.jsonl. */
final class EvalResults {

    private EvalResults() {
    }

    /**
     * One turn as the customer would have seen it, with its check results.
     *
     * @param toolsOk           null when the case does not score tool choice for this turn
     * @param pricingViolation  a currency amount appeared although noCurrency was true
     */
    record TurnResult(String message, String country, String reply, String mode, String fallbackReason,
            List<String> tools, List<String> actionTypes, long latencyMs, long inputTokens, long outputTokens,
            double estimatedCostUsd, List<String> failures, Boolean toolsOk, boolean pricingViolation) {
    }

    /** @param status "pass" or "fail" */
    record CaseResult(String id, String category, String status, List<String> failures, List<TurnResult> turns,
            String model, String finishedAt) {

        boolean passed() {
            return "pass".equals(status);
        }
    }
}
