package com.kaarislabs.chatbot.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class InputSanitizerTest {

    @Test
    void removesControlAndInvisibleCharactersButKeepsNewlines() {
        assertThat(InputSanitizer.clean("BA\u0000044\u0007 price​?\nthanks‮"))
                .isEqualTo("BA044 price?\nthanks");
    }

    @Test
    void collapsesWhitespaceAndTrims() {
        assertThat(InputSanitizer.clean("  Do   you\t\thave   BA044?  ")).isEqualTo("Do you have BA044?");
        assertThat(InputSanitizer.clean("line one  \r\n\r\n\n   line two")).isEqualTo("line one\nline two");
    }

    @Test
    void onlyInvisibleCharactersBecomeEmpty() {
        assertThat(InputSanitizer.clean("\u0000​ \t\n")).isEmpty();
        assertThat(InputSanitizer.clean(null)).isNull();
    }
}
