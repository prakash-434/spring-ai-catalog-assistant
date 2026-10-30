package com.kaarislabs.chatbot.tools;

import static com.kaarislabs.chatbot.tools.ToolJson.assertNoLinksOrBrand;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.EnumMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.support.ToolCallbacks;

import com.kaarislabs.chatbot.chat.ChatAction;
import com.kaarislabs.chatbot.chat.ChatContext;
import com.kaarislabs.chatbot.service.DocumentService;
import com.kaarislabs.chatbot.service.dto.DocumentAvailability;
import com.kaarislabs.chatbot.service.dto.DocumentType;
import com.kaarislabs.chatbot.tools.ToolResults.Documents;
import com.kaarislabs.chatbot.tools.ToolSupport.ToolError;

@ExtendWith(MockitoExtension.class)
class DocumentToolsTest {

    @Mock
    DocumentService documentService;

    ChatContext context;
    DocumentTools tools;

    @BeforeEach
    void setUp() {
        context = new ChatContext();
        tools = new DocumentTools(documentService, context);
    }

    private static Map<DocumentType, Boolean> flags(DocumentType... available) {
        Map<DocumentType, Boolean> docs = new EnumMap<>(DocumentType.class);
        for (DocumentType t : DocumentType.values()) {
            docs.put(t, false);
        }
        for (DocumentType t : available) {
            docs.put(t, true);
        }
        return docs;
    }

    @Test
    void foundBatchRecordsOneCoaActionPerAvailableDocument() {
        when(documentService.checkDocuments("KL-01-00012", "KLBF01053")).thenReturn(new DocumentAvailability(
                "KL-01-00012", "KLBF01053", true, flags(DocumentType.COA, DocumentType.NMR), "/documents"));

        Object result = tools.checkDocuments("KL-01-00012", "KLBF01053");

        Documents docs = (Documents) result;
        assertThat(docs.found()).isTrue();
        assertThat(docs.availableDocuments()).containsExactly("Certificate of Analysis", "1H NMR");
        assertNoLinksOrBrand(result);
        assertThat(context.actions()).containsExactly(
                ChatAction.coa("KL-01-00012", "KLBF01053", "COA"),
                ChatAction.coa("KL-01-00012", "KLBF01053", "NMR"));
    }

    @Test
    void unknownBatchPointsToTheDocumentsPage() {
        when(documentService.checkDocuments("KL-01-00012", "NOPE1")).thenReturn(
                new DocumentAvailability("KL-01-00012", "NOPE1", false, flags(), "/documents"));

        Documents docs = (Documents) tools.checkDocuments("KL-01-00012", "NOPE1");

        assertThat(docs.found()).isFalse();
        assertThat(docs.availableDocuments()).isEmpty();
        assertThat(context.actions()).containsExactly(ChatAction.page("/documents"));
    }

    @Test
    void realBatchFormatsAreAccepted() {
        when(documentService.checkDocuments("KL-01-00012", "KR/001/23")).thenReturn(
                new DocumentAvailability("KL-01-00012", "KR/001/23", false, flags(), "/documents"));

        assertThat(tools.checkDocuments("KL-01-00012", "KR/001/23")).isInstanceOf(Documents.class);
    }

    /** The model repeats examples to customers, and batch numbers must never be suggested. */
    @Test
    void toolDefinitionContainsNoExampleBatchNumber() {
        var definition = ToolCallbacks.from(tools)[0].getToolDefinition();

        assertThat(definition.description() + definition.inputSchema())
                .doesNotContain("KLBF", "KLAH", "CAT459", "KR/001");
    }

    @Test
    void missingOrUnsafeBatchIsRejectedBeforeTheService() {
        assertThat(tools.checkDocuments("KL-01-00012", null)).isInstanceOf(ToolError.class);
        assertThat(tools.checkDocuments("KL-01-00012", "KLBF'--")).isInstanceOf(ToolError.class);
        verify(documentService, never()).checkDocuments(anyString(), anyString());
        assertThat(context.actions()).isEmpty();
    }
}
