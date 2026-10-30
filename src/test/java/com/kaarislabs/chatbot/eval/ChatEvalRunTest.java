package com.kaarislabs.chatbot.eval;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import com.kaarislabs.chatbot.chat.ChatAction;
import com.kaarislabs.chatbot.chat.ChatContext;
import com.kaarislabs.chatbot.chat.ChatReply;
import com.kaarislabs.chatbot.chat.ChatStreamService;
import com.kaarislabs.chatbot.common.InputSanitizer;
import com.kaarislabs.chatbot.eval.EvalResults.CaseResult;
import com.kaarislabs.chatbot.eval.EvalResults.TurnResult;
import com.kaarislabs.chatbot.stats.ChatStats;

import tools.jackson.databind.json.JsonMapper;

/**
 * Runs cases.yaml against the real model and database: {@code mvnw -Peval test} (not part of the normal build).
 * Each case gets a fresh session; each turn goes through {@link ChatStreamService#answer}, the same path as POST
 * /chat minus HTTP, so the output guard and the fallback are part of what is measured.
 * <p>
 * Results are appended to target/eval/results-&lt;model&gt;.jsonl as each case finishes, and a later run skips the
 * cases already there. A rate limit (HTTP 429 / quota) stops the run cleanly: the case in progress and all later
 * ones stay pending and are never scored as failures. Options: {@code -Deval.delaySeconds=5} between turns.
 */
@Tag("eval")
@EnabledIfEnvironmentVariable(named = "CHATBOT_DB_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "GEMINI_API_KEY", matches = ".+")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ChatEvalRunTest {

    static final Path OUTPUT_DIR = Path.of("target", "eval");
    static final Path BASELINE_DIR = Path.of("eval");

    @Autowired
    ChatStreamService streams;

    @Autowired
    ChatStats stats;

    @Autowired
    JsonMapper json;

    @Value("${spring.ai.google.genai.chat.model}")
    String model;

    /** A turn whose fallback means "try again later", not a wrong answer. */
    private static boolean quotaStop(String fallbackReason) {
        return ChatStreamService.MODEL_RATE_LIMITED.equals(fallbackReason)
                || ChatStreamService.CIRCUIT_OPEN.equals(fallbackReason);
    }

    @Test
    void runEvaluation() throws Exception {
        Duration delay = Duration.ofSeconds(Long.getLong("eval.delaySeconds", 5));
        String safeModel = model.replaceAll("[^A-Za-z0-9._-]", "_");
        Path resultsFile = OUTPUT_DIR.resolve("results-" + safeModel + ".jsonl");

        List<EvalCase> cases = EvalCase.load(json);
        Map<String, CaseResult> results = readResults(resultsFile);
        List<EvalCase> todo = cases.stream().filter(c -> !results.containsKey(c.id())).toList();
        System.out.printf("%n[eval] model=%s cases=%d finished=%d to run=%d delay=%ss%n", model, cases.size(),
                cases.size() - todo.size(), todo.size(), delay.toSeconds());

        boolean firstTurn = true;
        String stoppedBy = null;
        for (EvalCase evalCase : todo) {
            String sessionId = UUID.randomUUID().toString();
            List<TurnResult> turns = new ArrayList<>();
            for (EvalCase.Turn turn : evalCase.turns()) {
                if (!firstTurn) {
                    Thread.sleep(delay.toMillis());
                }
                firstTurn = false;
                TurnResult result = runTurn(sessionId, turn);
                if (quotaStop(result.fallbackReason())) {
                    stoppedBy = result.fallbackReason();
                    break;
                }
                turns.add(result);
            }
            if (stoppedBy != null) {
                break;
            }
            CaseResult caseResult = toCaseResult(evalCase, turns);
            append(resultsFile, caseResult);
            results.put(evalCase.id(), caseResult);
            System.out.printf("[eval] %-34s %s%s%n", evalCase.id(), caseResult.status().toUpperCase(),
                    caseResult.passed() ? "" : "  " + caseResult.failures());
        }

        EvalReport.Summary summary = EvalReport.summarize(model, cases, results);
        Path report = OUTPUT_DIR.resolve("report-" + safeModel + ".md");
        EvalReport.writeMarkdown(summary, cases, results, report);
        EvalReport.writeBaseline(summary, BASELINE_DIR.resolve("baseline-" + safeModel + ".json"), json);

        System.out.printf("%n[eval] finished %d/%d, passed %d, pending %d%s%n", summary.casesDone(),
                summary.casesTotal(), summary.casesPassed(), summary.casesPending(),
                stoppedBy == null ? "" : "  (stopped: " + stoppedBy + "; run the same command later to resume)");
        System.out.printf("[eval] accuracy %s%%, tool selection %s%%, pricing violations %d, report %s%n",
                summary.answerAccuracyPct(), summary.toolSelectionAccuracyPct(), summary.pricingRuleViolations(),
                report.toAbsolutePath());
    }

    private TurnResult runTurn(String sessionId, EvalCase.Turn turn) {
        ChatContext context = new ChatContext(sessionId, turn.country());
        String message = InputSanitizer.clean(turn.message());
        long start = System.nanoTime();
        ChatReply reply = streams.answer(context, message);
        long latencyMs = (System.nanoTime() - start) / 1_000_000;

        List<String> actionTypes = reply.actions().stream().map(ChatAction::type).distinct().toList();
        EvalScorer.Score score = EvalScorer.score(turn,
                new EvalScorer.Observed(reply.reply(), reply.mode(), reply.toolsCalled(), actionTypes));
        return new TurnResult(turn.message(), turn.country(), reply.reply(), reply.mode(), context.fallbackReason(),
                reply.toolsCalled(), actionTypes, latencyMs, context.inputTokens(), context.outputTokens(),
                stats.estimatedCost(context.inputTokens(), context.outputTokens()).doubleValue(), score.failures(),
                score.toolsOk(), score.pricingViolation());
    }

    private CaseResult toCaseResult(EvalCase evalCase, List<TurnResult> turns) {
        List<String> failures = new ArrayList<>();
        for (int i = 0; i < turns.size(); i++) {
            for (String f : turns.get(i).failures()) {
                failures.add(turns.size() == 1 ? f : "turn " + (i + 1) + ": " + f);
            }
        }
        return new CaseResult(evalCase.id(), evalCase.category(), failures.isEmpty() ? "pass" : "fail", failures,
                turns, model, OffsetDateTime.now().toString());
    }

    private Map<String, CaseResult> readResults(Path file) throws IOException {
        Map<String, CaseResult> results = new LinkedHashMap<>();
        if (Files.exists(file)) {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                if (!line.isBlank()) {
                    CaseResult r = json.readValue(line, CaseResult.class);
                    results.put(r.id(), r);
                }
            }
        }
        return results;
    }

    private void append(Path file, CaseResult result) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, json.writeValueAsString(result) + "\n", StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }
}
