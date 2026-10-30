package com.kaarislabs.chatbot.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

/** The scoring rules and the case file itself; no model involved. */
class EvalScorerTest {

    static EvalCase.Turn turn(List<String> mustContain, List<String> mustNotContain, List<String> tools,
            Boolean noCurrency, List<String> actions, List<String> modes) {
        return new EvalCase.Turn("q", "IN", mustContain, mustNotContain, tools, noCurrency, actions, modes);
    }

    @Test
    void passingReplyHasNoFailures() {
        EvalScorer.Score score = EvalScorer.score(
                turn(List.of("BA044", "re:in\\s+stock"), List.of("KLBF"), List.of("searchProducts|getProductDetails"),
                        true, List.of("product"), null),
                new EvalScorer.Observed("Yes, BA044 is In Stock.", "llm", List.of("getProductDetails"),
                        List.of("product", "quote")));

        assertThat(score.failures()).isEmpty();
        assertThat(score.toolsOk()).isTrue();
        assertThat(score.pricingViolation()).isFalse();
    }

    @Test
    void everyFailedCheckIsReported() {
        EvalScorer.Score score = EvalScorer.score(
                turn(List.of("Synthesis on Demand"), List.of("re:\\bis in stock\\b"), List.of("getPackSizesAndPrice"),
                        true, List.of("quote"), List.of("llm")),
                new EvalScorer.Observed("It is in stock for $345.00.", "fallback", List.of("searchProducts"),
                        List.of("product")));

        assertThat(score.failures()).hasSize(6);
        assertThat(score.failures()).anyMatch(f -> f.startsWith("PRICING RULE"));
        assertThat(score.toolsOk()).isFalse();
        assertThat(score.pricingViolation()).isTrue();
    }

    @Test
    void emptyExpectedToolsMeansNoCalls() {
        assertThat(EvalScorer.toolProblems(List.of(), List.of())).isEmpty();
        assertThat(EvalScorer.toolProblems(List.of(), List.of("searchProducts"))).hasSize(1);
        assertThat(EvalScorer.toolProblems(List.of("a|b", "c"), List.of("c", "b"))).isEmpty();
    }

    @Test
    void unscoredToolsStayNull() {
        assertThat(EvalScorer.score(turn(null, null, null, null, null, null),
                new EvalScorer.Observed("ok", "llm", List.of("x"), List.of())).toolsOk()).isNull();
    }

    @Test
    void caseFileHasFortyCasesInTheAgreedMix() {
        List<EvalCase> cases = EvalCase.load(JsonMapper.builder().build());

        Map<String, Long> perCategory = cases.stream()
                .collect(Collectors.groupingBy(EvalCase::category, LinkedHashMap::new, Collectors.counting()));
        assertThat(cases).hasSize(40);
        assertThat(perCategory).containsExactlyInAnyOrderEntriesOf(Map.ofEntries(
                Map.entry("lookup_catno", 4L), Map.entry("lookup_cas", 3L), Map.entry("lookup_name", 4L),
                Map.entry("details_followup", 4L), Map.entry("stock", 3L), Map.entry("pricing", 6L),
                Map.entry("coa", 4L), Map.entry("browse", 3L), Map.entry("company", 2L),
                Map.entry("out_of_scope", 4L), Map.entry("injection", 2L), Map.entry("non_english", 1L)));
        assertThat(cases).allSatisfy(c -> assertThat(c.turns()).isNotEmpty()
                .allSatisfy(t -> assertThat(t.message()).isNotBlank()));
        // Regexes in the file must compile.
        cases.stream().flatMap(c -> c.turns().stream())
                .flatMap(t -> Stream.concat(t.mustContainOrEmpty().stream(),
                        t.mustNotContainOrEmpty().stream()))
                .forEach(check -> EvalScorer.matches("", check));
    }
}
