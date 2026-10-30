package com.kaarislabs.chatbot.service.dto;

/** Batch document types, same labels as CoaModel.DOCUMENT_TYPES on the website. */
public enum DocumentType {
    COA("Certificate of Analysis"),
    HPLC("HPLC"),
    MASS("Mass Spectrum"),
    NMR("1H NMR"),
    C13_NMR("13C NMR"),
    IR("IR Spectrum"),
    TGA("TGA");

    private final String label;

    DocumentType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
