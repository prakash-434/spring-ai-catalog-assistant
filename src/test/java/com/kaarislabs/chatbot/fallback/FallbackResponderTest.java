package com.kaarislabs.chatbot.fallback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.kaarislabs.chatbot.chat.ChatAction;
import com.kaarislabs.chatbot.config.CompanyProperties;
import com.kaarislabs.chatbot.guard.ToolResultOutputGuard;
import com.kaarislabs.chatbot.service.PricingService;
import com.kaarislabs.chatbot.service.ProductService;
import com.kaarislabs.chatbot.service.dto.PackSizePrice;
import com.kaarislabs.chatbot.service.dto.PricingResult;
import com.kaarislabs.chatbot.service.dto.PricingStatus;
import com.kaarislabs.chatbot.service.dto.ProductDetail;
import com.kaarislabs.chatbot.service.dto.ProductSearchResult;
import com.kaarislabs.chatbot.service.dto.ProductSummary;
import com.kaarislabs.chatbot.stats.ChatStats;

class FallbackResponderTest {

    final ProductService products = mock(ProductService.class);
    final PricingService pricing = mock(PricingService.class);
    final ChatStats stats = new ChatStats(new BigDecimal("1.50"), new BigDecimal("9.00"));
    final FallbackResponder responder = new FallbackResponder(products, pricing,
            new CompanyProperties("Kaaris", "https://kaarislabs.com", "addr", "+91", "sales@kaarislabs.com",
                    new CompanyProperties.Links("/contact", "/documents", "/complaints", "/stock-list")),
            stats);

    static ProductDetail ba044() {
        return new ProductDetail("BA044", "Salbutamol-D9 acetate", "Stable Isotopes", "Salbutamol", "1781417-68-2",
                null, "C13H12D9NO3 x C2H4O2", "308.42", "In Stock", null, null, null, null, null, null, null,
                ">98.0 atom% D (MS)", null, "Witega", "/product/ba044");
    }

    static ProductSummary summary(String catNo, String name, String stock) {
        return new ProductSummary(catNo, name, "Impurity", "Famotidine", "1781417-68-2", null, null, stock, null,
                "/product/" + catNo.toLowerCase());
    }

    private void priced(String catNo, String country, PricingStatus status) {
        List<PackSizePrice> packs = List.of(new PackSizePrice(catNo, "5 mg", BigDecimal.valueOf(5), "MG",
                new BigDecimal("345"), "$345.00"));
        when(pricing.getPricing(catNo, country)).thenReturn(new PricingResult(catNo, country, status,
                status == PricingStatus.PRICED ? "USD" : null, packs, null));
    }

    private static void assertNoCurrency(FallbackResponder.Answer answer) {
        assertThat(ToolResultOutputGuard.currencyAmounts(answer.text())).isEmpty();
        assertThat(answer.text()).doesNotContain("345", "$", "₹", "USD", "INR");
    }

    @Test
    void catNoMessageGivesTheProductSummaryWithoutPrices() {
        when(products.findByCatNo("BA044")).thenReturn(Optional.of(ba044()));
        priced("BA044", "US", PricingStatus.PRICED);

        FallbackResponder.Answer answer = responder.answer("Do you have ba044?", "US");

        assertThat(answer.text()).isEqualTo("""
                Salbutamol-D9 acetate (Cat No BA044)
                CAS: 1781417-68-2
                Stock: In Stock
                Pack sizes and prices are shown on the product page.""");
        assertThat(answer.actions()).containsExactly(ChatAction.product("BA044"), ChatAction.quote("BA044"));
        assertNoCurrency(answer);
    }

    @Test
    void productWithoutShownPricesAsksForAQuote() {
        when(products.findByCatNo("BA044")).thenReturn(Optional.of(ba044()));
        priced("BA044", "IN", PricingStatus.CONTACT_FOR_PRICING);

        FallbackResponder.Answer answer = responder.answer("BA044 price?", "IN");

        assertThat(answer.text()).endsWith("Please request a quote.");
        assertNoCurrency(answer);
    }

    @Test
    void casMessageListsTheMatches() {
        when(products.search("1781417-68-2", null, 5)).thenReturn(new ProductSearchResult("1781417-68-2", 2, List.of(
                summary("BA044", "Salbutamol-D9 acetate", "In Stock"),
                summary("KL-05-03243", "Salbutamol D9 acetate", "Synthesis on Demand"))));
        priced("BA044", "IN", PricingStatus.CONTACT_FOR_QUOTE);
        priced("KL-05-03243", "IN", PricingStatus.CONTACT_FOR_QUOTE);

        FallbackResponder.Answer answer = responder.answer("Do you have CAS 1781417-68-2?", "IN");

        assertThat(answer.text()).startsWith("I found 2 products for CAS 1781417-68-2.")
                .contains("Salbutamol-D9 acetate (Cat No BA044)", "Stock: Synthesis on Demand");
        assertThat(answer.actions())
                .containsExactly(ChatAction.product("BA044"), ChatAction.product("KL-05-03243"));
        verify(products, never()).findByCatNo(anyString());
        assertNoCurrency(answer);
    }

    @Test
    void freeTextSearchListsTopFiveWithTheTotal() {
        List<ProductSummary> five = List.of(
                summary("KL-02-32505", "Famotidine Acid Methyl Ester Hydrochloride", "Synthesis on Demand"),
                summary("KL-02-11666", "Famotidine Amide Impurity Hydrochloride", "Synthesis on Demand"),
                summary("KL-02-11656", "Famotidine Chloromethyl Impurity", "Synthesis on Demand"),
                summary("KL-02-11662", "Famotidine Cyanoethyl Impurity", "In Stock, Delivery in 2-3 Days"),
                summary("KL-02-07909", "Famotidine degradation impurity 2", "Synthesis on Demand"));
        when(products.search("famotidine impurity", null, 5))
                .thenReturn(new ProductSearchResult("famotidine impurity", 48, five));

        FallbackResponder.Answer answer = responder.answer("Do you have any famotidine impurity?", "IN");

        assertThat(answer.text()).startsWith("Here are the closest matches I found:")
                .contains("- KL-02-11662: Famotidine Cyanoethyl Impurity (In Stock, Delivery in 2-3 Days)")
                .endsWith("48 products found in total.");
        assertThat(answer.actions()).hasSize(5).allMatch(a -> a.type().equals("product"));
        verify(pricing, never()).getPricing(anyString(), anyString());
        assertNoCurrency(answer);
    }

    @Test
    void nothingFoundOffersContactAndQuote() {
        when(products.search(eq("xyzzy nonsense"), any(), anyInt()))
                .thenReturn(new ProductSearchResult("xyzzy nonsense", 0, List.of()));

        FallbackResponder.Answer answer = responder.answer("xyzzy nonsense", "IN");

        assertThat(answer.text()).isEqualTo(FallbackResponder.NOTHING_FOUND);
        assertThat(answer.actions()).containsExactly(ChatAction.page("/contact"), ChatAction.quote(null));
        assertThat(stats.snapshotAndReset("test")).contains("zeroResultSearches=1");
    }

    @Test
    void onlyStopWordsMeansNothingFoundWithoutASearch() {
        FallbackResponder.Answer answer = responder.answer("Hi, can you show me the price?", "IN");

        assertThat(answer.text()).isEqualTo(FallbackResponder.NOTHING_FOUND);
        verify(products, never()).search(anyString(), any(), anyInt());
    }

    @Test
    void unknownCatNoFallsBackToSearch() {
        when(products.findByCatNo("ZZ999")).thenReturn(Optional.empty());
        when(products.search("zz999", null, 5)).thenReturn(new ProductSearchResult("zz999", 0, List.of()));

        assertThat(responder.answer("ZZ999", "IN").text()).isEqualTo(FallbackResponder.NOTHING_FOUND);
    }

    @Test
    void detectsCatNosAndCasNumbers() {
        assertThat(FallbackResponder.catNoCandidates("Do you have BA044? Or KL-13-00645.")).containsExactly(
                "BA044", "KL-13-00645");
        assertThat(FallbackResponder.catNoCandidates("price for famotidine")).isEmpty();
        assertThat(FallbackResponder.casNumber("cas 1781417-68-2 please")).contains("1781417-68-2");
        assertThat(FallbackResponder.casNumber("KL-13-00645")).isEmpty();
    }
}
