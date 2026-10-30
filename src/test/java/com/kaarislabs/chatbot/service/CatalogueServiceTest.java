package com.kaarislabs.chatbot.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.kaarislabs.chatbot.common.InvalidInputException;
import com.kaarislabs.chatbot.config.CatalogueProperties;
import com.kaarislabs.chatbot.repository.CategoryRepository;
import com.kaarislabs.chatbot.repository.ProductRepository;
import com.kaarislabs.chatbot.repository.StockStatusFilter;
import com.kaarislabs.chatbot.repository.row.NameCount;
import com.kaarislabs.chatbot.repository.row.Page;

@ExtendWith(MockitoExtension.class)
class CatalogueServiceTest {

    @Mock
    CategoryRepository categories;

    @Mock
    ProductRepository products;

    CatalogueService service;

    @BeforeEach
    void setUp() {
        service = new CatalogueService(categories, products, new CatalogueProperties(List.of("Metabolites"), List.of()));
    }

    @Test
    void categoriesCarryWebsiteLinks() {
        when(categories.findAllWithCounts()).thenReturn(List.of(new NameCount("Stable Isotopes", 3772), new NameCount("API", 1720)));

        assertThat(service.categories()).extracting(c -> c.url())
                .containsExactly("/categories/stable-isotopes", "/categories/api");
    }

    @Test
    void slugMatchesWebsite() {
        assertThat(CatalogueService.categorySlug("Pesticide Standards")).isEqualTo("pesticide-standards");
        assertThat(CatalogueService.categorySlug("GC standard")).isEqualTo("gc-standard");
        assertThat(CatalogueService.categorySlug("A & B  (x)")).isEqualTo("a-and-b-x");
    }

    @Test
    void browseAcceptsWebsiteKeysAndStoredLabels() {
        when(products.browse(any(), any(), any(), anyInt())).thenReturn(new Page<>(0, List.of()));

        assertThat(service.browse("API", null, "in_stock_2_3_days", 5).stockStatus()).isEqualTo("In Stock, Delivery in 2-3 Days");
        assertThat(service.browse("API", null, "synthesis on demand", 5).stockStatus()).isEqualTo("Synthesis on Demand");
        assertThat(service.browse("API", null, "all", 5).stockStatus()).isNull();
        verify(products).browse("API", null, StockStatusFilter.IN_STOCK_2_3_DAYS, 5);
    }

    @Test
    void unknownStockStatusIsRejected() {
        assertThatThrownBy(() -> service.browse("API", null, "available soon", 5)).isInstanceOf(InvalidInputException.class);
    }

    @Test
    void excludedCategoryIsNotQueried() {
        assertThat(service.browse("Metabolites", null, null, 5).total()).isZero();
        assertThat(service.apiFamilies("Metabolites", null, 5).families()).isEmpty();
        verify(products, never()).browse(any(), any(), any(), anyInt());
        verify(categories, never()).findApiFamilies(any(), any(), anyInt());
    }
}
