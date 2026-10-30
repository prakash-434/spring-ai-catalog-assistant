package com.kaarislabs.chatbot.repository.row;

/**
 * Which OIBT document columns are filled for one batch. The SQL computes the flags so file paths never leave the
 * database.
 */
public record BatchDocumentsRow(
        String itemCode,
        String batchNum,
        boolean coa,
        boolean hplc,
        boolean mass,
        boolean nmr,
        boolean c13Nmr,
        boolean ir,
        boolean tga) {
}
