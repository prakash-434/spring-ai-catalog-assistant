package com.kaarislabs.chatbot.eval;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.kaarislabs.chatbot.chat.ChatStreamService;
import com.kaarislabs.chatbot.eval.EvalResults.CaseResult;
import com.kaarislabs.chatbot.eval.EvalResults.TurnResult;

import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

/** Scores over all finished cases, written as a Markdown report and a JSON baseline. */
final class EvalReport {

    private EvalReport() {
    }

    record CategoryScore(String category, int cases, int done, int passed, Double accuracyPct) {
    }

    /** Percentages are null when there is nothing to divide by yet. */
    record Summary(String model, String generatedAt, int casesTotal, int casesDone, int casesPending,
            int casesPassed, Double answerAccuracyPct, int turnsScoredForTools, Double toolSelectionAccuracyPct,
            int pricingRuleViolations, int guardViolations, int fallbackTurns, Double fallbackRatePct, int turns,
            Long avgLatencyMs, Long p95LatencyMs, Long avgTokensPerTurn, Double avgCostPerMessageUsd,
            List<CategoryScore> categories, List<String> pendingCaseIds) {
    }

    static Summary summarize(String model, List<EvalCase> cases, Map<String, CaseResult> results) {
        List<CaseResult> done = cases.stream().map(c -> results.get(c.id())).filter(r -> r != null).toList();
        List<TurnResult> turns = done.stream().flatMap(r -> r.turns().stream()).toList();
        int passed = (int) done.stream().filter(CaseResult::passed).count();

        List<TurnResult> toolTurns = turns.stream().filter(t -> t.toolsOk() != null).toList();
        int toolsOk = (int) toolTurns.stream().filter(TurnResult::toolsOk).count();
        int fallbackTurns = (int) turns.stream().filter(t -> "fallback".equals(t.mode())).count();
        int guardViolations = (int) turns.stream()
                .filter(t -> ChatStreamService.GUARD_VIOLATION.equals(t.fallbackReason())).count();
        int pricingViolations = (int) turns.stream().filter(TurnResult::pricingViolation).count();

        List<Long> latencies = turns.stream().map(TurnResult::latencyMs).sorted().toList();
        Long avgLatency = latencies.isEmpty() ? null
                : Math.round(latencies.stream().mapToLong(Long::longValue).average().orElse(0));
        Long p95 = latencies.isEmpty() ? null
                : latencies.get(Math.max(0, (int) Math.ceil(latencies.size() * 0.95) - 1));
        Long avgTokens = turns.isEmpty() ? null
                : Math.round(turns.stream().mapToLong(t -> t.inputTokens() + t.outputTokens()).average().orElse(0));
        Double avgCost = turns.isEmpty() ? null
                : round(turns.stream().mapToDouble(TurnResult::estimatedCostUsd).average().orElse(0), 6);

        Map<String, List<EvalCase>> byCategory = cases.stream()
                .collect(Collectors.groupingBy(EvalCase::category, LinkedHashMap::new, Collectors.toList()));
        List<CategoryScore> categories = new ArrayList<>();
        byCategory.forEach((category, list) -> {
            List<CaseResult> catDone = list.stream().map(c -> results.get(c.id())).filter(r -> r != null).toList();
            int catPassed = (int) catDone.stream().filter(CaseResult::passed).count();
            categories.add(new CategoryScore(category, list.size(), catDone.size(), catPassed,
                    pct(catPassed, catDone.size())));
        });

        List<String> pending = cases.stream().map(EvalCase::id).filter(id -> !results.containsKey(id)).toList();
        return new Summary(model, OffsetDateTime.now().toString(), cases.size(), done.size(), pending.size(), passed,
                pct(passed, done.size()), toolTurns.size(), pct(toolsOk, toolTurns.size()), pricingViolations,
                guardViolations, fallbackTurns, pct(fallbackTurns, turns.size()), turns.size(), avgLatency, p95,
                avgTokens, avgCost, categories, pending);
    }

    static void writeBaseline(Summary summary, Path file, JsonMapper json) throws IOException {
        Files.createDirectories(file.getParent());
        String text = json.rebuild().enable(SerializationFeature.INDENT_OUTPUT).build().writeValueAsString(summary);
        Files.writeString(file, text + "\n", StandardCharsets.UTF_8);
    }

    static void writeMarkdown(Summary s, List<EvalCase> cases, Map<String, CaseResult> results, Path file)
            throws IOException {
        StringBuilder md = new StringBuilder();
        md.append("# Chatbot evaluation: ").append(s.model()).append("\n\n");
        md.append("Generated ").append(s.generatedAt()).append(". ");
        if (s.casesPending() > 0) {
            md.append("**Incomplete: ").append(s.casesPending()).append(" of ").append(s.casesTotal())
                    .append(" cases pending** (resume with `mvnw -Peval test`).");
        } else {
            md.append("All ").append(s.casesTotal()).append(" cases finished.");
        }
        md.append("\n\n## Summary\n\n| Metric | Value |\n|---|---|\n");
        row(md, "Cases finished", s.casesDone() + " / " + s.casesTotal());
        row(md, "Answer accuracy (all checks pass)", pctText(s.answerAccuracyPct()) + " (" + s.casesPassed() + ")");
        row(md, "Tool selection accuracy", pctText(s.toolSelectionAccuracyPct()) + " of "
                + s.turnsScoredForTools() + " scored turns");
        row(md, "Pricing-rule violations (must be 0)", String.valueOf(s.pricingRuleViolations()));
        row(md, "Guard violations", String.valueOf(s.guardViolations()));
        row(md, "Fallback rate", pctText(s.fallbackRatePct()) + " (" + s.fallbackTurns() + " of " + s.turns()
                + " turns)");
        row(md, "Latency avg / p95", ms(s.avgLatencyMs()) + " / " + ms(s.p95LatencyMs()));
        row(md, "Tokens per turn (avg)", s.avgTokensPerTurn() == null ? "n/a" : s.avgTokensPerTurn().toString());
        row(md, "Estimated cost per message (avg)", s.avgCostPerMessageUsd() == null ? "n/a"
                : String.format("$%.4f", s.avgCostPerMessageUsd()));

        md.append("\n## Per category\n\n| Category | Cases | Finished | Passed | Accuracy |\n|---|---|---|---|---|\n");
        for (CategoryScore c : s.categories()) {
            md.append("| ").append(c.category()).append(" | ").append(c.cases()).append(" | ").append(c.done())
                    .append(" | ").append(c.passed()).append(" | ").append(pctText(c.accuracyPct())).append(" |\n");
        }

        md.append("\n## Failures\n\n");
        boolean any = false;
        for (EvalCase c : cases) {
            CaseResult r = results.get(c.id());
            if (r == null || r.passed()) {
                continue;
            }
            any = true;
            md.append("### ").append(c.id()).append(" (").append(c.category()).append(")\n\n");
            for (String f : r.failures()) {
                md.append("- ").append(f).append('\n');
            }
            for (int i = 0; i < r.turns().size(); i++) {
                TurnResult t = r.turns().get(i);
                md.append("\n**Turn ").append(i + 1).append("** (").append(t.country() == null ? "unknown" : t.country())
                        .append(", mode ").append(t.mode())
                        .append(t.fallbackReason() == null ? "" : " / " + t.fallbackReason())
                        .append(", tools ").append(t.tools()).append(", actions ").append(t.actionTypes())
                        .append("): ").append(t.message()).append("\n\n");
                md.append("```text\n").append(t.reply()).append("\n```\n");
            }
            md.append('\n');
        }
        if (!any) {
            md.append("None.\n");
        }
        if (!s.pendingCaseIds().isEmpty()) {
            md.append("\n## Pending\n\n").append(String.join(", ", s.pendingCaseIds())).append('\n');
        }
        Files.createDirectories(file.getParent());
        Files.writeString(file, md.toString(), StandardCharsets.UTF_8);
    }

    private static void row(StringBuilder md, String name, String value) {
        md.append("| ").append(name).append(" | ").append(value).append(" |\n");
    }

    private static Double pct(int part, int whole) {
        return whole == 0 ? null : round(100.0 * part / whole, 1);
    }

    private static double round(double value, int decimals) {
        double factor = Math.pow(10, decimals);
        return Math.round(value * factor) / factor;
    }

    private static String pctText(Double pct) {
        return pct == null ? "n/a" : pct + "%";
    }

    private static String ms(Long ms) {
        return ms == null ? "n/a" : ms + " ms";
    }
}
