package com.kaarislabs.chatbot.repository;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * The LIKE patterns for one product search, ported from buildSearchClause()/cleanQuery() in the website's
 * searchlistrepository.ts.
 *
 * @param trimmed  the query as typed (trimmed); also used for the exact-match ranking
 * @param term1    {@code %trimmed%}, escaped (strategy 1)
 * @param term2    {@code %cleaned%}, escaped, or null when no prefix was stripped (strategy 2)
 * @param tokens   {@code %token%} per whitespace-separated token, escaped; empty for single-token queries (strategy 3)
 */
record SearchTerms(String trimmed, String term1, String term2, List<String> tokens) {

    /** Escape character used in every LIKE clause: {@code ... LIKE :p ESCAPE '\'}. */
    static final char ESCAPE = '\\';

    private static final int MAX_TOKENS = 5;

    // Same prefixes, same order, as cleanQuery() on the website.
    private static final List<Pattern> PREFIXES = List.of(
            Pattern.compile("^\"?chem\\.?\\s*name\\s*:\\s*", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^\"?chemical\\s*name\\s*:\\s*", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^\"?iupac\\s*name\\s*:\\s*", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^\"?iupac\\s*:\\s*", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^cas\\s*(no\\.?|number)?\\s*:?\\s*", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^cat(alog)?\\s*no\\.?\\s*:?\\s*", Pattern.CASE_INSENSITIVE));

    static SearchTerms of(String query) {
        String trimmed = query.trim();
        String cleaned = cleanQuery(trimmed);

        String term1 = "%" + escapeLike(trimmed) + "%";
        String term2 = cleaned.equals(trimmed) ? null : "%" + escapeLike(cleaned) + "%";

        List<String> words = Arrays.stream(cleaned.split("\\s+")).filter(w -> !w.isEmpty()).limit(MAX_TOKENS).toList();
        List<String> tokens = words.size() > 1 ? words.stream().map(w -> "%" + escapeLike(w) + "%").toList() : List.of();

        return new SearchTerms(trimmed, term1, term2, tokens);
    }

    static String cleanQuery(String trimmed) {
        String result = trimmed;
        for (Pattern prefix : PREFIXES) {
            result = prefix.matcher(result).replaceFirst("");
        }
        return result.trim();
    }

    /**
     * Makes %, _, [ and the escape character itself literal for {@code LIKE ... ESCAPE '\'}. Chemical names often contain
     * brackets (e.g. "[1,2,4]triazolo"), so these must match literally.
     */
    static String escapeLike(String value) {
        StringBuilder out = new StringBuilder(value.length() + 8);
        for (char c : value.toCharArray()) {
            if (c == ESCAPE || c == '%' || c == '_' || c == '[') {
                out.append(ESCAPE);
            }
            out.append(c);
        }
        return out.toString();
    }
}
