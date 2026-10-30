package com.kaarislabs.chatbot.repository;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SearchTermsTest {

    @Test
    void prefixesAreStrippedLikeTheWebsite() {
        assertThat(SearchTerms.cleanQuery("CAS No: 1781417-68-2")).isEqualTo("1781417-68-2");
        assertThat(SearchTerms.cleanQuery("cas number 50-00-0")).isEqualTo("50-00-0");
        assertThat(SearchTerms.cleanQuery("Cat No. BA044")).isEqualTo("BA044");
        assertThat(SearchTerms.cleanQuery("catalog no BA044")).isEqualTo("BA044");
        assertThat(SearchTerms.cleanQuery("Chemical Name: Famotidine")).isEqualTo("Famotidine");
        assertThat(SearchTerms.cleanQuery("IUPAC: benzene")).isEqualTo("benzene");
        assertThat(SearchTerms.cleanQuery("Famotidine")).isEqualTo("Famotidine");
    }

    @Test
    void singleTermUsesStrategyOneOnly() {
        SearchTerms terms = SearchTerms.of("BA044");

        assertThat(terms.term1()).isEqualTo("%BA044%");
        assertThat(terms.term2()).isNull();
        assertThat(terms.tokens()).isEmpty();
    }

    @Test
    void prefixedQueryAddsCleanedTerm() {
        SearchTerms terms = SearchTerms.of("CAS No: 1781417-68-2");

        assertThat(terms.trimmed()).isEqualTo("CAS No: 1781417-68-2");
        assertThat(terms.term2()).isEqualTo("%1781417-68-2%");
    }

    @Test
    void multiWordQueryAddsUpToFiveTokens() {
        assertThat(SearchTerms.of("Famotidine 76824-35-6").tokens()).containsExactly("%Famotidine%", "%76824-35-6%");
        assertThat(SearchTerms.of("a b c d e f g").tokens()).hasSize(5);
    }

    @Test
    void likeWildcardsAndBracketsAreEscaped() {
        assertThat(SearchTerms.escapeLike("[1,2,4]triazolo")).isEqualTo("\\[1,2,4]triazolo");
        assertThat(SearchTerms.escapeLike("50%_x\\y")).isEqualTo("50\\%\\_x\\\\y");
    }
}
