package com.kaarislabs.chatbot.service.dto;

import java.util.List;

/** Search hits, best match first; {@code total} counts every match, {@code items} is capped. */
public record ProductSearchResult(String query, long total, List<ProductSummary> items) {
}
