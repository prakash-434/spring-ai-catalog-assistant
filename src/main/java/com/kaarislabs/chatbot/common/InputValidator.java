package com.kaarislabs.chatbot.common;

import java.util.regex.Pattern;

/**
 * Validates user-supplied identifiers before they reach a query. Every method returns the trimmed value or throws
 * {@link InvalidInputException}.
 */
public final class InputValidator {

    public static final int MAX_CODE_LENGTH = 40;
    public static final int MAX_QUERY_LENGTH = 200;
    public static final int MAX_NAME_LENGTH = 100;

    private static final Pattern CAS = Pattern.compile("^\\d{2,7}-\\d{2}-\\d$");
    // Character sets taken from the real data: OITM.ItemCode also has '.' and an inner space ("KL-13-00274-2.5 L"),
    // OIBT.BatchNum also has '_' and '/' ("CAT459_1", "KR/001/23"). Quotes, ';', '*', '%' stay blocked and "--" is
    // rejected separately, so no SQL quote or comment sequence can pass.
    private static final Pattern CODE = Pattern.compile("^[A-Za-z0-9.-]+( [A-Za-z0-9.-]+)*$");
    private static final Pattern BATCH = Pattern.compile("^[A-Za-z0-9_/-]+$");

    private InputValidator() {
    }

    public static boolean isCasNumber(String value) {
        return value != null && CAS.matcher(value.trim()).matches();
    }

    public static String requireCas(String value) {
        if (!isCasNumber(value)) {
            throw new InvalidInputException("A CAS number looks like 1781417-68-2.");
        }
        return value.trim();
    }

    /** Catalogue number / item code: letters, digits, hyphens, dots and single inner spaces, at most 40 characters. */
    public static String requireCatNo(String value) {
        return requireCode(value, CODE, "catalogue number", "letters, digits, hyphens, dots and single spaces");
    }

    public static String requireItemCode(String value) {
        return requireCode(value, CODE, "item code", "letters, digits, hyphens, dots and single spaces");
    }

    /** Batch number: letters, digits, hyphens, underscores and slashes, at most 40 characters. */
    public static String requireBatchNumber(String value) {
        return requireCode(value, BATCH, "batch number", "letters, digits, hyphens, underscores and slashes");
    }

    public static String requireSearchQuery(String value) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty()) {
            throw new InvalidInputException("Please enter something to search for.");
        }
        if (trimmed.length() > MAX_QUERY_LENGTH) {
            throw new InvalidInputException("Search text can be at most " + MAX_QUERY_LENGTH + " characters.");
        }
        return trimmed;
    }

    /** Category or API family name: free text used only as a bound parameter, so only the length is checked. */
    public static String optionalName(String value, String label) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() > MAX_NAME_LENGTH) {
            throw new InvalidInputException("The " + label + " can be at most " + MAX_NAME_LENGTH + " characters.");
        }
        return trimmed;
    }

    public static int clampLimit(int requested, int defaultLimit, int maxLimit) {
        if (requested <= 0) {
            return defaultLimit;
        }
        return Math.min(requested, maxLimit);
    }

    private static String requireCode(String value, Pattern pattern, String label, String allowed) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.length() > MAX_CODE_LENGTH || trimmed.contains("--") || !pattern.matcher(trimmed).matches()) {
            throw new InvalidInputException("A " + label + " may contain only " + allowed + " (max "
                    + MAX_CODE_LENGTH + " characters).");
        }
        return trimmed;
    }
}
