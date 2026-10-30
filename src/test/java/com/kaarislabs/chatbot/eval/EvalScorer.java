package com.kaarislabs.chatbot.eval;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import com.kaarislabs.chatbot.guard.ToolResultOutputGuard;

/** Checks one reply against its turn's expectations; every failed check gives one human-readable reason. */
final class EvalScorer {

    static final String REGEX_PREFIX = "re:";

    private EvalScorer() {
    }

    /** What the bot did in one turn, before scoring. */
    record Observed(String reply, String mode, List<String> tools, List<String> actionTypes) {
    }

    /** @param toolsOk null when the turn does not score tool choice */
    record Score(List<String> failures, Boolean toolsOk, boolean pricingViolation) {
    }

    static Score score(EvalCase.Turn turn, Observed observed) {
        List<String> failures = new ArrayList<>();
        String reply = observed.reply() == null ? "" : observed.reply();

        for (String expected : turn.mustContainOrEmpty()) {
            if (!matches(reply, expected)) {
                failures.add("missing " + quote(expected));
            }
        }
        for (String forbidden : turn.mustNotContainOrEmpty()) {
            if (matches(reply, forbidden)) {
                failures.add("contains forbidden " + quote(forbidden));
            }
        }

        boolean pricingViolation = false;
        if (Boolean.TRUE.equals(turn.noCurrency())) {
            List<String> amounts = ToolResultOutputGuard.currencyAmounts(reply);
            if (!amounts.isEmpty()) {
                pricingViolation = true;
                failures.add("PRICING RULE: shows " + amounts.getFirst() + " although no price may be shown");
            }
        }

        Boolean toolsOk = null;
        if (turn.expectedTools() != null) {
            List<String> problems = toolProblems(turn.expectedTools(), observed.tools());
            toolsOk = problems.isEmpty();
            failures.addAll(problems);
        }

        if (turn.expectedActions() != null) {
            for (String type : turn.expectedActions()) {
                if (!observed.actionTypes().contains(type)) {
                    failures.add("no \"" + type + "\" action");
                }
            }
        }

        if (!turn.modes().contains(observed.mode())) {
            failures.add("mode " + observed.mode() + " not in " + turn.modes());
        }
        return new Score(failures, toolsOk, pricingViolation);
    }

    /** Every expected entry ("a" or "a|b") needs a matching call; an empty expectation means no calls at all. */
    static List<String> toolProblems(List<String> expected, List<String> called) {
        List<String> problems = new ArrayList<>();
        if (expected.isEmpty()) {
            if (!called.isEmpty()) {
                problems.add("expected no tool calls, got " + called);
            }
            return problems;
        }
        for (String entry : expected) {
            List<String> options = List.of(entry.split("\\|"));
            if (called.stream().noneMatch(options::contains)) {
                problems.add("tool " + entry + " not called (called " + called + ")");
            }
        }
        return problems;
    }

    static boolean matches(String reply, String expectation) {
        if (expectation.startsWith(REGEX_PREFIX)) {
            return Pattern.compile(expectation.substring(REGEX_PREFIX.length()),
                    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE).matcher(reply).find();
        }
        return reply.toLowerCase(Locale.ROOT).contains(expectation.toLowerCase(Locale.ROOT));
    }

    private static String quote(String expectation) {
        return "\"" + expectation + "\"";
    }
}
