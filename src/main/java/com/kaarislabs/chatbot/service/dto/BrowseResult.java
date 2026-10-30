package com.kaarislabs.chatbot.service.dto;

import java.util.List;

/** Products matching category / API family / stock filters (null = not filtered), ordered by name. */
public record BrowseResult(
        String category,
        String apiFamily,
        String stockStatus,
        long total,
        List<ProductSummary> items) {
}
