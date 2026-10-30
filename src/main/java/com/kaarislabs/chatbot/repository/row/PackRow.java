package com.kaarislabs.chatbot.repository.row;

import java.math.BigDecimal;

/**
 * One [@PRICING_R] pack row with both raw prices. Only {@code PricingService} may read the prices; they must never be
 * passed on unfiltered.
 */
public record PackRow(
        String itemCode,
        BigDecimal quantity,
        String uom,
        BigDecimal priceInr,
        BigDecimal priceUsd,
        String indiaPriceDisplay) {
}
