package com.kaarislabs.chatbot.repository.row;

/** Raw OITM/OITB product columns, as stored in SAP. Detail-only columns are null in list queries. */
public record ProductRow(
        String itemCode,
        String itemName,
        String category,
        String apiFamily,
        String casNo,
        String altCasNo,
        String molecularFormula,
        String molecularWeight,
        String inventoryStatus,
        String iupacName,
        String synonyms,
        String smiles,
        String solvent,
        String stableIsotopesType,
        String storageTemp,
        String productFormat,
        String purity,
        String shippingTemp,
        String brand) {
}
