package com.kaarislabs.chatbot.tools;

import java.util.List;
import java.util.Map;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import com.kaarislabs.chatbot.chat.ChatAction;
import com.kaarislabs.chatbot.chat.ChatContext;
import com.kaarislabs.chatbot.common.InputValidator;
import com.kaarislabs.chatbot.service.DocumentService;
import com.kaarislabs.chatbot.service.dto.DocumentAvailability;
import com.kaarislabs.chatbot.service.dto.DocumentType;
import com.kaarislabs.chatbot.tools.ToolResults.Documents;

/** Batch document (COA etc.) availability. Needs item code and batch; there is no way to list batches. */
@Component
public class DocumentTools {

    private final DocumentService documentService;
    private final ChatContext context;

    public DocumentTools(DocumentService documentService, ChatContext context) {
        this.documentService = documentService;
        this.context = context;
    }

    @Tool(name = "checkDocuments", description = """
            Check which batch documents exist for one product batch: Certificate of Analysis (COA), HPLC, Mass \
            Spectrum, 1H NMR, 13C NMR, IR Spectrum and TGA. Needs both the item code (Cat No) and the batch number \
            exactly as the customer gave them; the batch number is printed on the vial label. Never call it with a \
            guessed or invented batch number. found=false means that item code and batch number do not match.""")
    public Object checkDocuments(
            @ToolParam(description = "Item code / Cat No, e.g. KL-01-00012.") String itemCode,
            // No example value: the model repeated a real batch number from here to a customer.
            @ToolParam(description = "Batch number exactly as the customer gave it from the vial label.")
            String batchNumber) {
        return ToolSupport.run(context, "checkDocuments", () -> {
            String item = InputValidator.requireItemCode(itemCode);
            String batch = InputValidator.requireBatchNumber(batchNumber);
            DocumentAvailability result = documentService.checkDocuments(item, batch);

            List<DocumentType> available = result.documents().entrySet().stream()
                    .filter(Map.Entry::getValue)
                    .map(Map.Entry::getKey)
                    .toList();
            if (result.found()) {
                available.forEach(d ->
                        context.addAction(ChatAction.coa(result.itemCode(), result.batchNum(), d.name())));
            } else if (result.pageUrl() != null) {
                context.addAction(ChatAction.page(result.pageUrl()));
            }
            return new Documents(result.itemCode(), result.batchNum(), result.found(),
                    available.stream().map(DocumentType::label).toList());
        });
    }
}
