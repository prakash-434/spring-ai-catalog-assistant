package com.kaarislabs.chatbot.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.kaarislabs.chatbot.config.CatalogueProperties;
import com.kaarislabs.chatbot.repository.ProductRepository;
import com.kaarislabs.chatbot.repository.row.Page;
import com.kaarislabs.chatbot.repository.row.ProductRow;
import com.kaarislabs.chatbot.service.dto.ProductDetail;
import com.kaarislabs.chatbot.service.dto.ProductSearchResult;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    ProductRepository repository;

    ProductService service;

    @BeforeEach
    void setUp() {
        service = new ProductService(repository,
                new CatalogueProperties(List.of("Metabolites", "Reagents", "Assets"), List.of("Assets")));
    }

    private static ProductRow detailRow(String cas, String altCas) {
        return new ProductRow("BA085", "\"Isoproterenol-D7 acetate\"", "Stable Isotopes", "Isoproterenol acetate",
                cas, altCas, "C11H10D7NO3 x CH3COOH", "278.35", "In Stock, Delivery in 2-3 Days",
                "\"4-[1-Hydroxy-2-(isopropyl-D7-amino)ethyl]-1,2-benzenediol acetate\"", "Isoprenaline-D7 acetate",
                null, "  ", "N/A", "2°C ~ 8°C Protected from light and Sealed", "Neat", "> 95%",
                "\"All Product are stable to be shipped at room temperature, unless otherwise specified\"", "NA");
    }

    @Test
    void detailIsCleanedForDisplay() {
        when(repository.findByCatNo("BA085")).thenReturn(Optional.of(detailRow("NA", "  ")));

        ProductDetail d = service.findByCatNo("BA085").orElseThrow();

        assertThat(d.name()).isEqualTo("Isoproterenol-D7 acetate");
        assertThat(d.casNo()).isNull();
        assertThat(d.altCasNo()).isNull();
        assertThat(d.iupacName()).isEqualTo("4-[1-Hydroxy-2-(isopropyl-D7-amino)ethyl]-1,2-benzenediol acetate");
        assertThat(d.shippingTemp()).startsWith("All Product are stable").doesNotContain("\"");
        assertThat(d.smiles()).isNull();
        assertThat(d.solvent()).isNull();
        assertThat(d.stableIsotopesType()).isNull();
        assertThat(d.brand()).isNull();
        assertThat(d.url()).isEqualTo("/product/ba085");
    }

    @Test
    void stockLabelIsReturnedExactlyAsStored() {
        when(repository.findByCatNo("BA085")).thenReturn(Optional.of(detailRow("1781417-68-2", null)));

        ProductDetail d = service.findByCatNo("BA085").orElseThrow();

        assertThat(d.stockStatus()).isEqualTo("In Stock, Delivery in 2-3 Days");
        assertThat(d.casNo()).isEqualTo("1781417-68-2");
    }

    @Test
    void searchMapsSummariesAndKeepsTotal() {
        ProductRow row = new ProductRow("BA044", "Salbutamol-D9 acetate", "Stable Isotopes", "Salbutamol", "n/a",
                null, "C13H12D9NO3", "308.42", "In Stock", null, null, null, null, null, null, null, null, null, null);
        when(repository.search("salbutamol", null, 10)).thenReturn(new Page<>(42, List.of(row)));

        ProductSearchResult result = service.search("  salbutamol ", null, 0);

        assertThat(result.total()).isEqualTo(42);
        assertThat(result.items()).singleElement().satisfies(s -> {
            assertThat(s.catNo()).isEqualTo("BA044");
            assertThat(s.casNo()).isNull();
            assertThat(s.url()).isEqualTo("/product/ba044");
        });
    }

    @Test
    void searchLimitIsCapped() {
        when(repository.search(eq("x"), any(), anyInt())).thenReturn(new Page<>(0, List.of()));

        service.search("x", null, 1000);

        verify(repository).search("x", null, ProductService.MAX_SEARCH_LIMIT);
    }

    @Test
    void searchInNonProductCategoryReturnsNothingWithoutQuerying() {
        ProductSearchResult result = service.search("anything", "Assets", 10);

        assertThat(result.total()).isZero();
        assertThat(result.items()).isEmpty();
        verify(repository, never()).search(anyString(), any(), anyInt());
    }

    @Test
    void searchStillCoversCategoriesThatAreOnlyHiddenFromBrowsing() {
        when(repository.search(eq("x"), eq("Metabolites"), anyInt())).thenReturn(new Page<>(0, List.of()));

        service.search("x", "Metabolites", 10);

        verify(repository).search("x", "Metabolites", 10);
    }
}
