package com.kaarislabs.chatbot.service;

import java.util.EnumMap;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.kaarislabs.chatbot.common.InputValidator;
import com.kaarislabs.chatbot.config.CompanyProperties;
import com.kaarislabs.chatbot.repository.BatchRepository;
import com.kaarislabs.chatbot.repository.row.BatchDocumentsRow;
import com.kaarislabs.chatbot.service.dto.DocumentAvailability;
import com.kaarislabs.chatbot.service.dto.DocumentType;

/**
 * Tells a customer which batch documents exist. Both the item code and the batch number are required; there is
 * deliberately no way to list the batches of a product, and file paths are never returned.
 */
@Service
public class DocumentService {

    private final BatchRepository batches;
    private final CompanyProperties company;

    public DocumentService(BatchRepository batches, CompanyProperties company) {
        this.batches = batches;
        this.company = company;
    }

    public DocumentAvailability checkDocuments(String itemCode, String batchNum) {
        String item = InputValidator.requireItemCode(itemCode);
        String batch = InputValidator.requireBatchNumber(batchNum);
        String pageUrl = company.links() == null ? null : company.links().documents();

        return batches.findByItemCodeAndBatch(item, batch)
                .map(row -> new DocumentAvailability(row.itemCode(), row.batchNum(), true, flags(row), pageUrl))
                .orElseGet(() -> new DocumentAvailability(item, batch, false, flags(null), pageUrl));
    }

    private static Map<DocumentType, Boolean> flags(BatchDocumentsRow row) {
        Map<DocumentType, Boolean> docs = new EnumMap<>(DocumentType.class);
        docs.put(DocumentType.COA, row != null && row.coa());
        docs.put(DocumentType.HPLC, row != null && row.hplc());
        docs.put(DocumentType.MASS, row != null && row.mass());
        docs.put(DocumentType.NMR, row != null && row.nmr());
        docs.put(DocumentType.C13_NMR, row != null && row.c13Nmr());
        docs.put(DocumentType.IR, row != null && row.ir());
        docs.put(DocumentType.TGA, row != null && row.tga());
        return docs;
    }
}
