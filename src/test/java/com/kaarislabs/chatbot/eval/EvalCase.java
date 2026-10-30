package com.kaarislabs.chatbot.eval;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import tools.jackson.databind.json.JsonMapper;

/** One evaluation case from src/test/resources/eval/cases.yaml. */
record EvalCase(String id, String category, List<Turn> turns) {

    static final String RESOURCE = "/eval/cases.yaml";

    /**
     * One message and what its reply must satisfy (see the header of cases.yaml).
     *
     * @param country       ISO code, or null for an unknown country
     * @param expectedTools null = not scored, empty = no tool may be called
     * @param allowedModes  null = only "llm"
     */
    record Turn(String message, String country, List<String> mustContain, List<String> mustNotContain,
            List<String> expectedTools, Boolean noCurrency, List<String> expectedActions, List<String> allowedModes) {

        List<String> mustContainOrEmpty() {
            return mustContain == null ? List.of() : mustContain;
        }

        List<String> mustNotContainOrEmpty() {
            return mustNotContain == null ? List.of() : mustNotContain;
        }

        List<String> modes() {
            return allowedModes == null ? List.of("llm") : allowedModes;
        }
    }

    static List<EvalCase> load(JsonMapper json) {
        try (InputStream in = EvalCase.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Missing " + RESOURCE);
            }
            List<Map<String, Object>> raw = new Yaml(new SafeConstructor(new LoaderOptions())).load(in);
            List<EvalCase> cases = raw.stream().map(m -> json.convertValue(m, EvalCase.class)).toList();
            long distinct = cases.stream().map(EvalCase::id).distinct().count();
            if (distinct != cases.size()) {
                throw new IllegalStateException("Duplicate case ids in " + RESOURCE);
            }
            return cases;
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read " + RESOURCE, e);
        }
    }
}
