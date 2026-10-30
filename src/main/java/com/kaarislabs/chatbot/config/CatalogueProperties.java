package com.kaarislabs.chatbot.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Catalogue settings from excluded-categories.yml.
 *
 * @param excludedCategories   OITB.ItmsGrpNam values hidden from category browsing (same list as the website
 *                             navigation); their products are still found by search and catalogue-number lookup
 * @param nonProductCategories OITB.ItmsGrpNam values that hold no chemical products; hidden everywhere
 */
@ConfigurationProperties("kaaris.catalogue")
public record CatalogueProperties(List<String> excludedCategories, List<String> nonProductCategories) {

    public CatalogueProperties {
        excludedCategories = excludedCategories == null ? List.of() : List.copyOf(excludedCategories);
        nonProductCategories = nonProductCategories == null ? List.of() : List.copyOf(nonProductCategories);
        if (!excludedCategories.containsAll(nonProductCategories)) {
            throw new IllegalArgumentException("Every non-product category must also be an excluded category");
        }
    }

    /** Hidden from category browsing. */
    public boolean isExcluded(String category) {
        return category != null && excludedCategories.contains(category);
    }

    /** Hidden from search and lookup as well. */
    public boolean isNonProduct(String category) {
        return category != null && nonProductCategories.contains(category);
    }
}
