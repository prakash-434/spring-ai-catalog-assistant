package com.kaarislabs.chatbot.tools;

import static com.kaarislabs.chatbot.tools.ToolJson.assertNoLinksOrBrand;
import static com.kaarislabs.chatbot.tools.ToolJson.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.kaarislabs.chatbot.chat.ChatAction;
import com.kaarislabs.chatbot.chat.ChatContext;
import com.kaarislabs.chatbot.common.InvalidInputException;
import com.kaarislabs.chatbot.service.CatalogueService;
import com.kaarislabs.chatbot.service.ProductService;
import com.kaarislabs.chatbot.service.dto.ApiFamilyInfo;
import com.kaarislabs.chatbot.service.dto.ApiFamilyList;
import com.kaarislabs.chatbot.service.dto.BrowseResult;
import com.kaarislabs.chatbot.service.dto.CategoryInfo;
import com.kaarislabs.chatbot.service.dto.ProductDetail;
import com.kaarislabs.chatbot.service.dto.ProductSearchResult;
import com.kaarislabs.chatbot.service.dto.ProductSummary;
import com.kaarislabs.chatbot.tools.ToolResults.NotFound;
import com.kaarislabs.chatbot.tools.ToolResults.ProductInfo;
import com.kaarislabs.chatbot.tools.ToolResults.ProductList;
import com.kaarislabs.chatbot.tools.ToolSupport.ToolError;

@ExtendWith(MockitoExtension.class)
class ProductToolsTest {

    @Mock
    ProductService productService;

    @Mock
    CatalogueService catalogueService;

    ChatContext context;
    ProductTools tools;

    @BeforeEach
    void setUp() {
        context = new ChatContext();
        tools = new ProductTools(productService, catalogueService, context);
    }

    static ProductSummary summary(String catNo) {
        return new ProductSummary(catNo, "Salbutamol-D9 acetate", "Stable Isotopes", "Salbutamol", "1781417-68-2",
                "C13H12D9NO3 x C2H4O2", "308.42", "In Stock", "Witega", "/product/" + catNo.toLowerCase());
    }

    static ProductDetail detail(String catNo) {
        return new ProductDetail(catNo, "Salbutamol-D9 acetate", "Stable Isotopes", "Salbutamol", "1781417-68-2",
                null, "C13H12D9NO3 x C2H4O2", "308.42", "In Stock", "iupac", "Albuterol-D9 acetate", null, null,
                "Deuterium", "2°C ~ 8°C", "Neat", ">98.0 atom% D (MS)", "room temperature", "Witega",
                "/product/" + catNo.toLowerCase());
    }

    @Test
    void searchCallsServiceWithAtMostFiveResultsAndRecordsProductActions() {
        when(productService.search("BA044", null, 5))
                .thenReturn(new ProductSearchResult("BA044", 1, List.of(summary("BA044"))));

        Object result = tools.searchProducts(" BA044 ", null, 50);

        verify(productService).search("BA044", null, 5);
        assertThat(result).isInstanceOf(ProductList.class);
        assertThat(((ProductList) result).total()).isEqualTo(1);
        assertThat(assertNoLinksOrBrand(result)).contains("\"catNo\":\"BA044\"", "In Stock");
        assertThat(context.actions()).containsExactly(ChatAction.product("BA044"));
        assertThat(context.toolsCalled()).containsExactly("searchProducts");
    }

    @Test
    void searchPassesCategoryAndDefaultLimit() {
        when(productService.search("famotidine", "Impurity", 5))
                .thenReturn(new ProductSearchResult("famotidine", 0, List.of()));

        Object result = tools.searchProducts("famotidine", "Impurity", null);

        assertThat(((ProductList) result).total()).isZero();
        assertThat(context.actions()).isEmpty();
    }

    @Test
    void blankSearchReturnsAnErrorWithoutCallingTheService() {
        Object result = tools.searchProducts("  ", null, 5);

        assertThat(result).isInstanceOf(ToolError.class);
        assertThat(json(result)).contains("Please enter something to search for.");
        verify(productService, never()).search(anyString(), any(), anyInt());
    }

    @Test
    void detailsRecordProductAndQuoteActionsAndHideLinksAndBrand() {
        when(productService.findByCatNo("BA044")).thenReturn(Optional.of(detail("BA044")));

        Object result = tools.getProductDetails("BA044");

        assertThat(result).isInstanceOf(ProductInfo.class);
        assertThat(assertNoLinksOrBrand(result)).contains("308.42", ">98.0 atom% D (MS)", "\"found\":true");
        assertThat(context.actions()).containsExactly(ChatAction.product("BA044"), ChatAction.quote("BA044"));
    }

    @Test
    void unknownProductReturnsNotFoundWithoutActions() {
        when(productService.findByCatNo("XX999")).thenReturn(Optional.empty());

        Object result = tools.getProductDetails("XX999");

        assertThat(result).isInstanceOf(NotFound.class);
        assertThat(json(result)).contains("\"found\":false");
        assertThat(context.actions()).isEmpty();
    }

    @Test
    void invalidCatNoIsRejectedBeforeTheService() {
        Object result = tools.getProductDetails("BA044'--");

        assertThat(result).isInstanceOf(ToolError.class);
        verify(productService, never()).findByCatNo(anyString());
    }

    @Test
    void categoriesAreListedWithoutUrls() {
        when(catalogueService.categories()).thenReturn(List.of(
                new CategoryInfo("Impurity", 9000, "/categories/impurity"),
                new CategoryInfo("Stable Isotopes", 800, "/categories/stable-isotopes")));

        Object result = tools.listCategories();

        assertThat(assertNoLinksOrBrand(result)).contains("Impurity", "Stable Isotopes", "9000");
        assertThat(context.actions()).isEmpty();
    }

    @Test
    void apiFamiliesNeedACategory() {
        when(catalogueService.apiFamilies("Impurity", "Famotidine", ProductTools.MAX_FAMILIES))
                .thenReturn(new ApiFamilyList("Impurity", 1, List.of(new ApiFamilyInfo("Famotidine", 12))));

        assertThat(json(tools.listApiFamilies("Impurity", "Famotidine"))).contains("Famotidine", "12");
        assertThat(tools.listApiFamilies(" ", "Famotidine")).isInstanceOf(ToolError.class);
        verify(catalogueService).apiFamilies("Impurity", "Famotidine", ProductTools.MAX_FAMILIES);
    }

    @Test
    void browseCallsServiceAndRecordsProductActions() {
        when(catalogueService.browse("Impurity", "Famotidine", "in_stock", 5))
                .thenReturn(new BrowseResult("Impurity", "Famotidine", "In Stock", 7,
                        List.of(summary("KL-01-00001"), summary("KL-01-00002"))));

        Object result = tools.browseProducts("Impurity", "Famotidine", "in_stock", 99);

        assertThat(((ProductList) result).total()).isEqualTo(7);
        assertNoLinksOrBrand(result);
        assertThat(context.actions())
                .containsExactly(ChatAction.product("KL-01-00001"), ChatAction.product("KL-01-00002"));
    }

    @Test
    void serviceValidationErrorsBecomeToolErrors() {
        when(catalogueService.browse("Impurity", null, "soon", 5))
                .thenThrow(new InvalidInputException("Unknown stock status."));

        Object result = tools.browseProducts("Impurity", null, "soon", null);

        assertThat(json(result)).contains("Unknown stock status.");
    }

    @Test
    void sixthToolCallInATurnIsRefused() {
        when(productService.search("x", null, 5)).thenReturn(new ProductSearchResult("x", 0, List.of()));
        for (int i = 0; i < ChatContext.MAX_TOOL_CALLS; i++) {
            tools.searchProducts("x", null, null);
        }

        Object result = tools.searchProducts("x", null, null);

        assertThat(result).isEqualTo(ToolSupport.LIMIT_REACHED);
        verify(productService, times(ChatContext.MAX_TOOL_CALLS)).search("x", null, 5);
        assertThat(context.toolsCalled()).hasSize(ChatContext.MAX_TOOL_CALLS + 1);
    }
}
