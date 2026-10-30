package com.kaarislabs.chatbot.service.dto;

import java.math.BigDecimal;

/**
 * One pack size with at most one price. {@code price} and {@code priceDisplay} are null when prices may not be shown;
 * when prices may be shown but this pack has none, {@code price} is null and {@code priceDisplay} is "contact".
 */
public record PackSizePrice(
        String itemCode,
        String packSize,
        BigDecimal quantity,
        String uom,
        BigDecimal price,
        String priceDisplay) {
}
