package com.kaarislabs.chatbot.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class InputValidatorTest {

    @ParameterizedTest
    @ValueSource(strings = {"1781417-68-2", "50-00-0", "7732-18-5", " 1228182-54-4 "})
    void validCasNumbers(String cas) {
        assertThat(InputValidator.isCasNumber(cas)).isTrue();
        assertThat(InputValidator.requireCas(cas)).isEqualTo(cas.trim());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "1-00-0", "12345678-00-0", "50-0-0", "50-00-00", "50000", "NA", "50-00-0a"})
    void invalidCasNumbers(String cas) {
        assertThat(InputValidator.isCasNumber(cas)).isFalse();
        assertThatThrownBy(() -> InputValidator.requireCas(cas)).isInstanceOf(InvalidInputException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"BA044", "KL-13-00645", "ba044", "KLBF01053", "A"})
    void validCodes(String code) {
        assertThat(InputValidator.requireCatNo(code)).isEqualTo(code);
        assertThat(InputValidator.requireItemCode(code)).isEqualTo(code);
        assertThat(InputValidator.requireBatchNumber(code)).isEqualTo(code);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "  ", "BA044'", "BA044\"", "BA044;", "BA044;--", "BA--044", "BA%", "BA*", "BA/*",
            "BA\t044", "BA\n044"})
    void invalidCodes(String code) {
        assertThatThrownBy(() -> InputValidator.requireCatNo(code)).isInstanceOf(InvalidInputException.class);
        assertThatThrownBy(() -> InputValidator.requireItemCode(code)).isInstanceOf(InvalidInputException.class);
        assertThatThrownBy(() -> InputValidator.requireBatchNumber(code)).isInstanceOf(InvalidInputException.class);
    }

    /** Real OITM.ItemCode formats: one code has a dot and an inner space. */
    @ParameterizedTest
    @ValueSource(strings = {"KL-13-00274-2.5 L", "BA.044"})
    void itemCodesAllowDotsAndInnerSpaces(String code) {
        assertThat(InputValidator.requireItemCode(code)).isEqualTo(code);
        assertThat(InputValidator.requireCatNo(code)).isEqualTo(code);
        assertThatThrownBy(() -> InputValidator.requireBatchNumber(code)).isInstanceOf(InvalidInputException.class);
    }

    /** Real OIBT.BatchNum formats include underscores and slashes. */
    @ParameterizedTest
    @ValueSource(strings = {"CAT459_1", "KR/001/23"})
    void batchNumbersAllowUnderscoresAndSlashes(String batch) {
        assertThat(InputValidator.requireBatchNumber(batch)).isEqualTo(batch);
        assertThatThrownBy(() -> InputValidator.requireItemCode(batch)).isInstanceOf(InvalidInputException.class);
    }

    @Test
    void itemCodeSpacesMustBeSingleAndInner() {
        assertThat(InputValidator.requireItemCode(" KL-13 2.5 L ")).isEqualTo("KL-13 2.5 L");
        assertThatThrownBy(() -> InputValidator.requireItemCode("KL-13  2.5")).isInstanceOf(InvalidInputException.class);
    }

    @Test
    void codeLengthLimitIs40() {
        assertThat(InputValidator.requireItemCode("A".repeat(40))).hasSize(40);
        assertThatThrownBy(() -> InputValidator.requireItemCode("A".repeat(41))).isInstanceOf(InvalidInputException.class);
        assertThat(InputValidator.requireBatchNumber("A".repeat(40))).hasSize(40);
        assertThatThrownBy(() -> InputValidator.requireBatchNumber("A".repeat(41))).isInstanceOf(InvalidInputException.class);
    }

    @Test
    void searchQueryIsTrimmedAndLimitedTo200Characters() {
        assertThat(InputValidator.requireSearchQuery("  Famotidine  ")).isEqualTo("Famotidine");
        assertThat(InputValidator.requireSearchQuery("x".repeat(200))).hasSize(200);
        assertThatThrownBy(() -> InputValidator.requireSearchQuery("x".repeat(201))).isInstanceOf(InvalidInputException.class);
        assertThatThrownBy(() -> InputValidator.requireSearchQuery("   ")).isInstanceOf(InvalidInputException.class);
        assertThatThrownBy(() -> InputValidator.requireSearchQuery(null)).isInstanceOf(InvalidInputException.class);
    }

    @Test
    void optionalNamesAndLimits() {
        assertThat(InputValidator.optionalName(null, "category")).isNull();
        assertThat(InputValidator.optionalName("  ", "category")).isNull();
        assertThat(InputValidator.optionalName(" Stable Isotopes ", "category")).isEqualTo("Stable Isotopes");
        assertThatThrownBy(() -> InputValidator.optionalName("x".repeat(101), "category"))
                .isInstanceOf(InvalidInputException.class);

        assertThat(InputValidator.clampLimit(0, 10, 50)).isEqualTo(10);
        assertThat(InputValidator.clampLimit(-5, 10, 50)).isEqualTo(10);
        assertThat(InputValidator.clampLimit(7, 10, 50)).isEqualTo(7);
        assertThat(InputValidator.clampLimit(500, 10, 50)).isEqualTo(50);
    }
}
