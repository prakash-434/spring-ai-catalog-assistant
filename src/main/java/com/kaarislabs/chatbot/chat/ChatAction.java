package com.kaarislabs.chatbot.chat;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A button the website shows under a reply. Recorded by the tools, never written by the model; the website turns each
 * one into a link, so no URL is built here except the relative page path from company-info.yml.
 *
 * @param type    "product", "quote", "coa" or "page"
 * @param docType a {@link com.kaarislabs.chatbot.service.dto.DocumentType} name, for "coa" only
 * @param path    website path, for "page" only
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChatAction(String type, String catNo, String itemCode, String batch, String docType, String path) {

    public static ChatAction product(String catNo) {
        return new ChatAction("product", catNo, null, null, null, null);
    }

    public static ChatAction quote(String catNo) {
        return new ChatAction("quote", catNo, null, null, null, null);
    }

    public static ChatAction coa(String itemCode, String batch, String docType) {
        return new ChatAction("coa", null, itemCode, batch, docType, null);
    }

    public static ChatAction page(String path) {
        return new ChatAction("page", null, null, null, null, path);
    }
}
