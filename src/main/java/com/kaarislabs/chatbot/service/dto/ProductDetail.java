package com.kaarislabs.chatbot.service.dto;

/** Everything the public product page shows, cleaned for display. {@code url} is relative to the website. */
public record ProductDetail(
        String catNo,
        String name,
        String category,
        String apiFamily,
        String casNo,
        String altCasNo,
        String molecularFormula,
        String molecularWeight,
        String stockStatus,
        String iupacName,
        String synonyms,
        String smiles,
        String solvent,
        String stableIsotopesType,
        String storageTemp,
        String productFormat,
        String purity,
        String shippingTemp,
        String brand,
        String url) {
}
