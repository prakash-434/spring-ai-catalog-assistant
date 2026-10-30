package com.kaarislabs.chatbot.tools;

import static com.kaarislabs.chatbot.tools.ToolJson.assertNoLinksOrBrand;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;

import com.kaarislabs.chatbot.chat.ChatAction;
import com.kaarislabs.chatbot.chat.ChatContext;
import com.kaarislabs.chatbot.service.PricingService;
import com.kaarislabs.chatbot.service.dto.PackSizePrice;
import com.kaarislabs.chatbot.service.dto.PricingResult;
import com.kaarislabs.chatbot.service.dto.PricingStatus;
import com.kaarislabs.chatbot.tools.ToolResults.Pricing;
import com.kaarislabs.chatbot.tools.ToolSupport.ToolError;

@ExtendWith(MockitoExtension.class)
class PricingToolsTest {

    @Mock
    PricingService pricingService;

    ChatContext context;
    PricingTools tools;

    @BeforeEach
    void setUp() {
        context = new ChatContext();
        tools = new PricingTools(pricingService, context);
    }

    private static PricingResult usdPriced() {
        return new PricingResult("BA072", "US", PricingStatus.PRICED, "USD", List.of(
                new PackSizePrice("BA072", "5 mg", new BigDecimal("5"), "MG", new BigDecimal("345"), "$345.00"),
                new PackSizePrice("BA072", "10 mg", new BigDecimal("10"), "MG", new BigDecimal("590"), "$590.00")),
                null);
    }

    @Test
    void usesTheCountryFromTheRequestContext() {
        context.setCountryCode("US");
        when(pricingService.getPricing("BA072", "US")).thenReturn(usdPriced());

        Object result = tools.getPackSizesAndPrice("BA072");

        verify(pricingService).getPricing("BA072", "US");
        Pricing pricing = (Pricing) result;
        assertThat(pricing.status()).isEqualTo("PRICED");
        assertThat(pricing.currency()).isEqualTo("USD");
        assertThat(pricing.packSizes()).extracting(ToolResults.Pack::price).containsExactly("$345.00", "$590.00");
        assertNoLinksOrBrand(result);
        assertThat(context.actions()).containsExactly(ChatAction.product("BA072"), ChatAction.quote("BA072"));
    }

    @Test
    void defaultCountryIsIndia() {
        when(pricingService.getPricing("BA072", "IN")).thenReturn(new PricingResult("BA072", "IN",
                PricingStatus.CONTACT_FOR_PRICING, null,
                List.of(new PackSizePrice("BA072", "5 mg", new BigDecimal("5"), "MG", null, null)), null));

        Pricing pricing = (Pricing) tools.getPackSizesAndPrice("BA072");

        verify(pricingService).getPricing("BA072", "IN");
        assertThat(pricing.status()).isEqualTo("CONTACT_FOR_PRICING");
        assertThat(pricing.packSizes()).extracting(ToolResults.Pack::price).containsOnlyNulls();
        assertThat(pricing.instruction()).contains("Do not state or estimate any price");
    }

    @Test
    void noPackSizesRecordsOnlyAQuoteAction() {
        when(pricingService.getPricing("BA044", "IN")).thenReturn(
                new PricingResult("BA044", "IN", PricingStatus.CONTACT_FOR_QUOTE, null, List.of(), null));

        Pricing pricing = (Pricing) tools.getPackSizesAndPrice("BA044");

        assertThat(pricing.status()).isEqualTo("CONTACT_FOR_QUOTE");
        assertThat(context.actions()).containsExactly(ChatAction.quote("BA044"));
    }

    @Test
    void invalidCatNoIsRejectedBeforeTheService() {
        assertThat(tools.getPackSizesAndPrice("BA072; DROP")).isInstanceOf(ToolError.class);
        verify(pricingService, never()).getPricing(anyString(), anyString());
    }

    @Test
    void theModelCannotChooseTheCountry() {
        ToolCallback[] callbacks = ToolCallbacks.from(tools);

        assertThat(callbacks).hasSize(1);
        String schema = callbacks[0].getToolDefinition().inputSchema();
        assertThat(schema).contains("catNo").doesNotContainIgnoringCase("country");
    }
}
