package com.kaarislabs.chatbot.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class TextCleanerTest {

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   ", "NA", "na", " N/A ", "n/a", "\"\"", "\"NA\""})
    void emptyAndNotAvailableValuesBecomeNull(String value) {
        assertThat(TextCleaner.clean(value)).isNull();
    }

    @Test
    void strayQuoteMarksAreStripped() {
        assertThat(TextCleaner.clean("\"All Product are stable to be shipped at room temperature\""))
                .isEqualTo("All Product are stable to be shipped at room temperature");
        assertThat(TextCleaner.clean("  \"4-[1-Hydroxy]-1,2-benzenediol acetate\" "))
                .isEqualTo("4-[1-Hydroxy]-1,2-benzenediol acetate");
    }

    @Test
    void ordinaryValuesAreTrimmedButOtherwiseUnchanged() {
        assertThat(TextCleaner.clean("  > 95% ")).isEqualTo("> 95%");
        assertThat(TextCleaner.clean("NaCl")).isEqualTo("NaCl");
        assertThat(TextCleaner.clean("2,6-\"di\"methyl")).isEqualTo("2,6-\"di\"methyl");
    }

    @Test
    void isValidCasMatchesWebsiteRule() {
        assertThat(TextCleaner.isValidCas("1781417-68-2")).isTrue();
        assertThat(TextCleaner.isValidCas(null)).isFalse();
        assertThat(TextCleaner.isValidCas("")).isFalse();
        assertThat(TextCleaner.isValidCas("  ")).isFalse();
        assertThat(TextCleaner.isValidCas("NA")).isFalse();
        assertThat(TextCleaner.isValidCas("na")).isFalse();
        assertThat(TextCleaner.isValidCas(" N/A ")).isFalse();
    }
}
