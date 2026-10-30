package com.kaarislabs.chatbot.service.dto;

/**
 * A product in a search or browse list. Text fields are cleaned (NA/blank -> null); {@code stockStatus} is
 * OITM.U_InventoryStatus exactly as stored.
 */
public record ProductSummary(
        String catNo,
        String name,
        String category,
        String apiFamily,
        String casNo,
        String molecularFormula,
        String molecularWeight,
        String stockStatus,
        String brand,
        String url) {
}
