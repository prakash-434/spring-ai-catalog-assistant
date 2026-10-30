package com.kaarislabs.chatbot.common;

import java.util.regex.Pattern;

/** Cleans free text from customers before it is validated or used. */
public final class InputSanitizer {

    // Control characters (Cc) except newline, and invisible format characters (Cf) such as zero-width spaces and
    // bidirectional overrides, which can hide text from a human reader.
    private static final Pattern INVISIBLE = Pattern.compile("[\\p{Cc}\\p{Cf}&&[^\\n]]");
    private static final Pattern WHITESPACE_WITH_NEWLINE = Pattern.compile("[ \\t\\u00A0\\p{Zs}]*\\n[\\s\\p{Zs}]*");
    private static final Pattern SPACES = Pattern.compile("[ \\t\\u00A0\\p{Zs}]+");

    private InputSanitizer() {
    }

    /**
     * Removes control and format characters (keeping newlines), collapses runs of whitespace (a run containing a
     * newline becomes one newline, any other run one space) and trims. Null stays null.
     */
    public static String clean(String text) {
        if (text == null) {
            return null;
        }
        // Tabs and carriage returns are control characters too, but they separate words: turn them into whitespace
        // before the control characters are removed.
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n').replace('\t', ' ');
        String cleaned = INVISIBLE.matcher(normalized).replaceAll("");
        cleaned = WHITESPACE_WITH_NEWLINE.matcher(cleaned).replaceAll("\n");
        cleaned = SPACES.matcher(cleaned).replaceAll(" ");
        return cleaned.strip();
    }
}
