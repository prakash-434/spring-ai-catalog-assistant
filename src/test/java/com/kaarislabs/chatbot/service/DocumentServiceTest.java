package com.kaarislabs.chatbot.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.kaarislabs.chatbot.common.InvalidInputException;
import com.kaarislabs.chatbot.config.CompanyProperties;
import com.kaarislabs.chatbot.repository.BatchRepository;
import com.kaarislabs.chatbot.repository.row.BatchDocumentsRow;
import com.kaarislabs.chatbot.service.dto.DocumentAvailability;
import com.kaarislabs.chatbot.service.dto.DocumentType;

@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {

    @Mock
    BatchRepository repository;

    DocumentService service;

    @BeforeEach
    void setUp() {
        CompanyProperties company = new CompanyProperties("Kaaris", "https://kaarislabs.com", "addr", "phone", "mail",
                new CompanyProperties.Links("/contact", "/documents", "/complaints", "/stock-list"));
        service = new DocumentService(repository, company);
    }

    @Test
    void reportsEachDocumentTypeInOrder() {
        when(repository.findByItemCodeAndBatch("KL-01-00012", "KLBF01053"))
                .thenReturn(Optional.of(new BatchDocumentsRow("KL-01-00012", "KLBF01053", true, true, false, false, false, true, false)));

        DocumentAvailability result = service.checkDocuments("KL-01-00012", "KLBF01053");

        assertThat(result.found()).isTrue();
        assertThat(result.pageUrl()).isEqualTo("/documents");
        assertThat(result.documents().keySet()).containsExactly(DocumentType.values());
        assertThat(result.documents()).containsEntry(DocumentType.COA, true)
                .containsEntry(DocumentType.HPLC, true)
                .containsEntry(DocumentType.IR, true)
                .containsEntry(DocumentType.NMR, false)
                .containsEntry(DocumentType.C13_NMR, false);
    }

    @Test
    void unknownCombinationReportsNotFoundAndNoDocuments() {
        when(repository.findByItemCodeAndBatch("BA044", "NOPE1")).thenReturn(Optional.empty());

        DocumentAvailability result = service.checkDocuments("BA044", "NOPE1");

        assertThat(result.found()).isFalse();
        assertThat(result.documents()).hasSize(DocumentType.values().length).doesNotContainValue(true);
    }

    @Test
    void resultTypeHasNoPathFields() {
        assertThat(Arrays.stream(DocumentAvailability.class.getRecordComponents()).map(c -> c.getName().toLowerCase()))
                .noneMatch(name -> name.contains("path") || name.contains("file"));
    }

    @Test
    void bothItemCodeAndBatchAreRequired() {
        assertThatThrownBy(() -> service.checkDocuments("BA044", "")).isInstanceOf(InvalidInputException.class);
        assertThatThrownBy(() -> service.checkDocuments(null, "KLBF01053")).isInstanceOf(InvalidInputException.class);
        assertThatThrownBy(() -> service.checkDocuments("BA044", "%")).isInstanceOf(InvalidInputException.class);
        verify(repository, never()).findByItemCodeAndBatch(anyString(), anyString());
    }
}
