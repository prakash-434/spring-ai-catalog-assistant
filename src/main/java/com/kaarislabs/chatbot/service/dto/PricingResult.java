package com.kaarislabs.chatbot.service.dto;

import java.util.List;

/**
 * The result of applying the website's pricing visibility rule for one visitor country.
 *
 * @param currency    "INR", "USD" or null when no price may be shown
 * @param countryCode the country the rule was applied for (unknown is treated as "IN")
 * @param gstNote     "18% GST extra" for Indian prices, otherwise null
 */
public record PricingResult(
        String catNo,
        String countryCode,
        PricingStatus status,
        String currency,
        List<PackSizePrice> packSizes,
        String gstNote) {
}
