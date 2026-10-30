package com.kaarislabs.chatbot.common;

/** Normalises SAP text fields before they reach a customer. */
public final class TextCleaner {

    private TextCleaner() {
    }

    /**
     * Returns null for NULL, blank, "NA" and "N/A" (case-insensitive); otherwise the trimmed value with stray literal
     * quote marks removed from both ends (several SAP fields are stored as {@code "All Product are stable..."}).
     */
    public static String clean(String value) {
        if (value == null) {
            return null;
        }
        String text = value.trim();
        while (text.length() >= 1 && (text.startsWith("\"") || text.endsWith("\""))) {
            if (text.startsWith("\"")) {
                text = text.substring(1);
            }
            if (text.endsWith("\"")) {
                text = text.substring(0, text.length() - 1);
            }
            text = text.trim();
        }
        if (text.isEmpty() || text.equalsIgnoreCase("NA") || text.equalsIgnoreCase("N/A")) {
            return null;
        }
        return text;
    }

    /**
     * Port of isValidCas() from the website's product page (app/(main)/product/[catno]/page.tsx): a CAS value is shown
     * only when it is not empty, "NA" or "N/A".
     */
    public static boolean isValidCas(String cas) {
        if (cas == null) {
            return false;
        }
        String normalized = cas.trim().toUpperCase();
        return !normalized.isEmpty() && !normalized.equals("NA") && !normalized.equals("N/A");
    }
}
