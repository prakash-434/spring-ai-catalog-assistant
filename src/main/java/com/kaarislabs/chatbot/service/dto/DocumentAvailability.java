package com.kaarislabs.chatbot.service.dto;

import java.util.Map;

/**
 * Which documents exist for one item code + batch number. Never contains file paths.
 *
 * @param found     false when the item code / batch combination does not exist (all documents are then false)
 * @param documents every {@link DocumentType}, in display order, with whether it exists
 * @param pageUrl   website page where the customer downloads the documents (relative)
 */
public record DocumentAvailability(
        String itemCode,
        String batchNum,
        boolean found,
        Map<DocumentType, Boolean> documents,
        String pageUrl) {
}
