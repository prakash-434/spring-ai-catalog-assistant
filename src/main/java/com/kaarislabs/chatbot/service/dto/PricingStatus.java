package com.kaarislabs.chatbot.service.dto;

public enum PricingStatus {
    /** Prices may be shown (per pack; a pack without a price reads "contact"). */
    PRICED,
    /** Pack sizes exist but prices may not be shown to this visitor. */
    CONTACT_FOR_PRICING,
    /** No pack sizes are defined; the customer must request a quote. */
    CONTACT_FOR_QUOTE
}
