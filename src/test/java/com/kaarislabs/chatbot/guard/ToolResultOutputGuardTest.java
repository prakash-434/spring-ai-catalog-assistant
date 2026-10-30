package com.kaarislabs.chatbot.guard;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.kaarislabs.chatbot.chat.ChatContext;
import com.kaarislabs.chatbot.chat.OutputGuard.Violation;
import com.kaarislabs.chatbot.chat.SessionEvidence;
import com.kaarislabs.chatbot.tools.ToolResults;

class ToolResultOutputGuardTest {

    final SessionEvidence evidence = new SessionEvidence(Duration.ofMinutes(30), 100);
    final ToolResultOutputGuard guard = new ToolResultOutputGuard(evidence);
    final ChatContext context = new ChatContext("s1", "US");

    static ToolResults.Pricing usdPricing() {
        return new ToolResults.Pricing("BA072", "PRICED", "USD", List.of(
                new ToolResults.Pack("5 mg", "$345.00"),
                new ToolResults.Pack("10 mg", "$590.00"),
                new ToolResults.Pack("25 mg", "$1,055.00"),
                new ToolResults.Pack("100 mg", "contact")), null, "State only these pack sizes and prices, in USD.");
    }

    static ToolResults.ProductInfo ba044() {
        return new ToolResults.ProductInfo(true, "BA044", "Salbutamol-D9 acetate", "Stable Isotopes", "Salbutamol",
                "1781417-68-2", "1420043-41-9 (unlabelled compound)", "C13H12D9NO3 x C2H4O2", "308.42", "In Stock",
                null, null, null, null, "Deuterium", "2°C ~ 8°C", "Neat", ">98.0 atom% D (MS)", null);
    }

    private Optional<Violation> check(String reply, Object... toolResults) {
        for (Object r : toolResults) {
            context.recordToolResult(r);
        }
        return guard.check(reply, "user question", context);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "5 mg: $345.00, 10 mg: $590.00, 25 mg: $1,055.00",
            "25 mg costs 1055.00 USD.",
            "25 mg is USD 1055",
            "25 mg is US$ 1,055",
            "The 5 mg pack is $345."})
    void pricesFromTheToolPassInAnyFormat(String reply) {
        assertThat(check(reply, usdPricing())).isEmpty();
    }

    @Test
    void inventedPriceIsBlocked() {
        assertThat(check("5 mg: $345.00, 50 mg: $1,999.00", usdPricing()))
                .contains(new Violation("unknown_price", "$1,999.00"));
    }

    @Test
    void anyPriceIsBlockedWhenPricesMayNotBeShown() {
        ToolResults.Pricing contact = new ToolResults.Pricing("BA072", "CONTACT_FOR_PRICING", null, List.of(
                new ToolResults.Pack("5 mg", null)), null, "Do not state or estimate any price.");

        assertThat(check("It is about ₹28,000 for 5 mg.", contact))
                .contains(new Violation("price_without_pricing", "₹28,000"));
        assertThat(check("Roughly Rs. 1,23,456 per pack.", contact)).map(Violation::rule)
                .contains("price_without_pricing");
    }

    @Test
    void anyPriceIsBlockedWhenNoPricingToolRan() {
        assertThat(check("BA044 is in stock and costs $345.00.", ba044())).map(Violation::rule)
                .contains("price_without_pricing");
    }

    @Test
    void productCodesNextToAPriceAreNotReadAsAmounts() {
        assertThat(check("BA072 $345.00 for 5 mg", usdPricing())).isEmpty();
    }

    @Test
    void percentagesMustComeFromTheTools() {
        assertThat(check("Purity: >98.0 atom% D (MS), i.e. 98%.", ba044())).isEmpty();
        assertThat(check("Purity is 99.5%.", ba044())).contains(new Violation("unknown_percentage", "99.5%"));
        assertThat(check("Purity is > 95 %.", ba044())).map(Violation::rule).contains("unknown_percentage");
    }

    @Test
    void gstNotePercentageIsAllowed() {
        ToolResults.Pricing inr = new ToolResults.Pricing("BA072", "PRICED", "INR", List.of(
                new ToolResults.Pack("5 mg", "₹28,750.00")), "18% GST extra", "State only these.");

        assertThat(check("5 mg: ₹28,750.00 (18% GST extra).", inr)).isEmpty();
    }

    @Test
    void casNumbersMustComeFromTheToolsOrTheCustomer() {
        assertThat(check("BA044 has CAS 1781417-68-2 (unlabelled 1420043-41-9).", ba044())).isEmpty();
        assertThat(check("Its CAS is 50-00-0.", ba044())).contains(new Violation("unknown_cas", "50-00-0"));

        context.recordToolResult(new ToolResults.ProductList(0, List.of()));
        assertThat(guard.check("We have nothing for CAS 7732-18-5.", "Do you have 7732-18-5?", context)).isEmpty();
    }

    @Test
    void molecularWeightAndOtherPlainNumbersPass() {
        assertThat(check("Molecular weight: 308.42 g/mol, 5 mg and 10 mg packs, 2-3 days.", ba044())).isEmpty();
    }

    /** Turn 1 read BA044's details; turn 2 answers "what is its purity?" from memory, without a tool call. */
    @Test
    void followUpFromMemoryUsesEarlierToolResultsOfTheSession() {
        evidence.record("s1", List.of(ba044()));
        ChatContext turn2 = new ChatContext("s1", "US");

        assertThat(guard.check("Its purity is >98.0 atom% D (MS).", "what is its purity?", turn2)).isEmpty();
        assertThat(guard.check("Its purity is 99.5%.", "what is its purity?", turn2))
                .contains(new Violation("unknown_percentage", "99.5%"));
        assertThat(guard.check("CAS 1781417-68-2.", "and the CAS?", turn2)).isEmpty();
    }

    @Test
    void evidenceOfAnotherSessionDoesNotCount() {
        evidence.record("other-session", List.of(ba044()));

        assertThat(guard.check("Its purity is >98.0 atom% D (MS).", "purity?", new ChatContext("s1", "US")))
                .map(Violation::rule).contains("unknown_percentage");
    }

    @Test
    void earlierPricesPassOnlyWhileTheLatestPricingResultAllowsThem() {
        evidence.record("s1", List.of(usdPricing()));
        ChatContext turn2 = new ChatContext("s1", "US");
        assertThat(guard.check("As I said, 5 mg is $345.00.", "repeat the price", turn2)).isEmpty();

        // A later pricing result that says "contact" switches prices off, even for figures seen earlier.
        evidence.record("s1", List.of(new ToolResults.Pricing("BA044", "CONTACT_FOR_QUOTE", null, List.of(), null,
                "No pack sizes or prices are listed.")));
        ChatContext turn3 = new ChatContext("s1", "US");
        assertThat(guard.check("BA072 5 mg is $345.00.", "and BA072 again?", turn3))
                .map(Violation::rule).contains("price_without_pricing");
    }

    @Test
    void replyWithoutToolsOrNumbersPasses() {
        assertThat(guard.check("I can help with Kaaris Labs products.", "hi", context)).isEmpty();
    }
}
