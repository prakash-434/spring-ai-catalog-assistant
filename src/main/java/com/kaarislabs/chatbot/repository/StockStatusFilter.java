package com.kaarislabs.chatbot.repository;

import java.util.Arrays;
import java.util.Optional;

/**
 * Stock filters, ported from STOCK_FILTER_MAP in the website's categorypagerepository.ts. Each maps to the exact
 * OITM.U_InventoryStatus label stored in SAP.
 */
public enum StockStatusFilter {
    IN_STOCK("in_stock", "In Stock"),
    ONGOING_IN_LAB("ongoing_in_lab", "Ongoing In Lab"),
    IN_STOCK_2_3_DAYS("in_stock_2_3_days", "In Stock, Delivery in 2-3 Days"),
    SYNTHESIS_ON_DEMAND("synthesis_on_demand", "Synthesis on Demand"),
    LIMITED_STOCK("limited_stock", "Limited Stock"),
    IN_QC_ANALYSIS("in_qc_analysis", "In QC Analysis");

    private final String key;
    private final String label;

    StockStatusFilter(String key, String label) {
        this.key = key;
        this.label = label;
    }

    public String key() {
        return key;
    }

    public String label() {
        return label;
    }

    /** Accepts the website key ("in_stock") or the stored label ("In Stock"), case-insensitively; empty for "all". */
    public static Optional<StockStatusFilter> parse(String value) {
        if (value == null || value.isBlank() || value.trim().equalsIgnoreCase("all")) {
            return Optional.empty();
        }
        String v = value.trim();
        return Arrays.stream(values())
                .filter(s -> s.key.equalsIgnoreCase(v) || s.label.equalsIgnoreCase(v))
                .findFirst();
    }
}
