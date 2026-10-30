package com.kaarislabs.chatbot.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.kaarislabs.chatbot.common.InvalidInputException;
import com.kaarislabs.chatbot.repository.PricingRepository;
import com.kaarislabs.chatbot.repository.row.PackRow;
import com.kaarislabs.chatbot.service.dto.PackSizePrice;
import com.kaarislabs.chatbot.service.dto.PricingResult;
import com.kaarislabs.chatbot.service.dto.PricingStatus;

@ExtendWith(MockitoExtension.class)
class PricingServiceTest {

    // Distinctive values so a leak shows up in toString().
    private static final BigDecimal INR = new BigDecimal("12345.67");
    private static final BigDecimal USD = new BigDecimal("987.65");

    @Mock
    PricingRepository repository;

    @InjectMocks
    PricingService service;

    private static PackRow row(String qty, BigDecimal inr, BigDecimal usd, String indiaFlag) {
        return new PackRow("BA044", new BigDecimal(qty), "MG", inr, usd, indiaFlag);
    }

    // 1. no pack rows -> CONTACT_FOR_QUOTE, no pack sizes
    @Test
    void noPackRowsMeansContactForQuoteWithNoPacksOrPrices() {
        when(repository.findPackRows("BA044")).thenReturn(List.of());

        PricingResult result = service.getPricing("BA044", "US");

        assertThat(result.status()).isEqualTo(PricingStatus.CONTACT_FOR_QUOTE);
        assertThat(result.currency()).isNull();
        assertThat(result.packSizes()).isEmpty();
        assertThat(result.gstNote()).isNull();
    }

    // 2. null / unknown country -> treated as IN
    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "  ", "XYZ", "1N", "India"})
    void unknownCountryIsTreatedAsIndia(String country) {
        when(repository.findPackRows("BA044")).thenReturn(List.of(row("5", INR, USD, "No")));

        PricingResult result = service.getPricing("BA044", country);

        assertThat(result.countryCode()).isEqualTo("IN");
        assertThat(result.status()).isEqualTo(PricingStatus.CONTACT_FOR_PRICING);
        assertThat(result.currency()).isNull();
    }

    // 3. IN and flag != 'Yes' -> CONTACT_FOR_PRICING, packs without prices
    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"No", "", "Y", "true"})
    void indiaWithoutFlagShowsPackSizesButNoPrices(String flag) {
        when(repository.findPackRows("BA044")).thenReturn(List.of(row("5", INR, USD, flag), row("10", INR, USD, flag)));

        PricingResult result = service.getPricing("BA044", "IN");

        assertThat(result.status()).isEqualTo(PricingStatus.CONTACT_FOR_PRICING);
        assertThat(result.currency()).isNull();
        assertThat(result.gstNote()).isNull();
        assertThat(result.packSizes()).extracting(PackSizePrice::packSize).containsExactly("5 mg", "10 mg");
        assertThat(result.packSizes()).allSatisfy(p -> {
            assertThat(p.price()).isNull();
            assertThat(p.priceDisplay()).isNull();
        });
        assertThat(result.toString()).doesNotContain("12345", "987");
    }

    // 4. IN and 'Yes' (any case) -> INR per pack + GST note
    @ParameterizedTest
    @ValueSource(strings = {"Yes", "YES", "yes", " Yes "})
    void indiaWithFlagShowsInrAndGstNote(String flag) {
        when(repository.findPackRows("BA044")).thenReturn(List.of(row("25", INR, USD, flag)));

        PricingResult result = service.getPricing("BA044", "in");

        assertThat(result.status()).isEqualTo(PricingStatus.PRICED);
        assertThat(result.currency()).isEqualTo("INR");
        assertThat(result.gstNote()).isEqualTo("18% GST extra");
        PackSizePrice pack = result.packSizes().getFirst();
        assertThat(pack.price()).isEqualByComparingTo(INR);
        assertThat(pack.priceDisplay()).contains("₹").contains("12,345.67");
        assertThat(result.toString()).doesNotContain("987");
    }

    // 5. IN, 'Yes', price <= 0 -> "contact", and never the USD price instead
    @Test
    void indiaPackWithoutInrPriceShowsContactNotUsd() {
        when(repository.findPackRows("BA044")).thenReturn(List.of(
                row("5", BigDecimal.ZERO, USD, "Yes"),
                row("10", new BigDecimal("-1"), USD, "Yes"),
                row("25", null, USD, "Yes")));

        PricingResult result = service.getPricing("BA044", "IN");

        assertThat(result.status()).isEqualTo(PricingStatus.PRICED);
        assertThat(result.currency()).isEqualTo("INR");
        assertThat(result.packSizes()).allSatisfy(p -> {
            assertThat(p.price()).isNull();
            assertThat(p.priceDisplay()).isEqualTo("contact");
        });
        assertThat(result.toString()).doesNotContain("987");
    }

    // 6. not IN -> USD per pack, no GST note, INR never shown
    @Test
    void outsideIndiaShowsUsdRegardlessOfIndiaFlag() {
        when(repository.findPackRows("BA044")).thenReturn(List.of(row("5", INR, USD, "No"), row("10", INR, USD, "Yes")));

        PricingResult result = service.getPricing("BA044", "us");

        assertThat(result.countryCode()).isEqualTo("US");
        assertThat(result.status()).isEqualTo(PricingStatus.PRICED);
        assertThat(result.currency()).isEqualTo("USD");
        assertThat(result.gstNote()).isNull();
        assertThat(result.packSizes()).allSatisfy(p -> {
            assertThat(p.price()).isEqualByComparingTo(USD);
            assertThat(p.priceDisplay()).isEqualTo("$987.65");
        });
        assertThat(result.toString()).doesNotContain("12345", "12,345");
    }

    // 7. not IN, USD <= 0 -> "contact", never the INR price instead
    @Test
    void outsideIndiaPackWithoutUsdPriceShowsContactNotInr() {
        when(repository.findPackRows("BA044")).thenReturn(List.of(row("5", INR, BigDecimal.ZERO, "Yes"), row("10", INR, null, "Yes")));

        PricingResult result = service.getPricing("BA044", "DE");

        assertThat(result.currency()).isEqualTo("USD");
        assertThat(result.packSizes()).allSatisfy(p -> {
            assertThat(p.price()).isNull();
            assertThat(p.priceDisplay()).isEqualTo("contact");
        });
        assertThat(result.toString()).doesNotContain("12345", "12,345");
    }

    @Test
    void indiaFlagIsAppliedPerItemForVariantItemCodes() {
        when(repository.findPackRows("KL-13-00645")).thenReturn(List.of(
                new PackRow("KL-13-00645", new BigDecimal("5"), "MG", INR, USD, "Yes"),
                new PackRow("KL-13-00645-A", new BigDecimal("10"), "MG", INR, USD, "No")));

        PricingResult result = service.getPricing("KL-13-00645", "IN");

        assertThat(result.status()).isEqualTo(PricingStatus.PRICED);
        assertThat(result.packSizes().get(0).price()).isEqualByComparingTo(INR);
        assertThat(result.packSizes().get(1).price()).isNull();
        assertThat(result.packSizes().get(1).priceDisplay()).isEqualTo("contact");
    }

    @Test
    void duplicatePackRowsAreCollapsed() {
        when(repository.findPackRows("BA044")).thenReturn(List.of(row("5", INR, USD, "No"), row("5.00", INR, USD, "No")));

        assertThat(service.getPricing("BA044", "US").packSizes()).hasSize(1);
    }

    @Test
    void invalidCatNoIsRejectedBeforeAnyQuery() {
        assertThatThrownBy(() -> service.getPricing("BA044'; DROP TABLE OITM--", "US"))
                .isInstanceOf(InvalidInputException.class);
        verify(repository, never()).findPackRows(anyString());
    }

    @Test
    void packLabelMatchesWebsiteFormat() {
        assertThat(PricingService.packLabel(new BigDecimal("25.000"), "MG")).isEqualTo("25 mg");
        assertThat(PricingService.packLabel(new BigDecimal("2.50"), "GM")).isEqualTo("2.5 gm");
        assertThat(PricingService.packLabel(new BigDecimal("1.255"), "ML")).isEqualTo("1.26 ml");
    }
}
