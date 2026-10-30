package com.kaarislabs.chatbot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Company contact details and website page links from company-info.yml. */
@ConfigurationProperties("kaaris.company")
public record CompanyProperties(
        String name,
        String website,
        String address,
        String phone,
        String email,
        Links links) {

    /** Paths relative to the website, e.g. "/documents". */
    public record Links(String quote, String documents, String complaints, String stockList) {
    }
}
