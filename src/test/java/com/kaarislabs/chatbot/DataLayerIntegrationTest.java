package com.kaarislabs.chatbot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.SQLException;
import java.util.List;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import com.kaarislabs.chatbot.config.CatalogueProperties;
import com.kaarislabs.chatbot.service.CatalogueService;
import com.kaarislabs.chatbot.service.PricingService;
import com.kaarislabs.chatbot.service.ProductService;
import com.kaarislabs.chatbot.service.dto.CategoryInfo;
import com.kaarislabs.chatbot.service.dto.ProductDetail;
import com.kaarislabs.chatbot.service.dto.ProductSearchResult;
import com.kaarislabs.chatbot.service.dto.PricingResult;
import com.kaarislabs.chatbot.service.dto.ProductSummary;

/**
 * Runs against the real database with the read-only "kaaris_chatbot" login. Skipped unless CHATBOT_DB_URL is set.
 * Run only these with: mvnw test -Dgroups=integration
 */
@Tag("integration")
@EnabledIfEnvironmentVariable(named = "CHATBOT_DB_URL", matches = ".+")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class DataLayerIntegrationTest {

    /** SQL Server error 229: "The SELECT permission was denied on the object ...". */
    private static final int PERMISSION_DENIED = 229;

    @Autowired
    ProductService productService;

    @Autowired
    CatalogueService catalogueService;

    @Autowired
    PricingService pricingService;

    @Autowired
    CatalogueProperties catalogue;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void searchByCatalogueNumberRanksExactMatchFirst() {
        ProductSearchResult result = productService.search("BA044", null, 10);

        assertThat(result.total()).isPositive();
        assertThat(result.items().getFirst().catNo()).isEqualTo("BA044");
    }

    @Test
    void searchByCasNumberFindsTheProduct() {
        ProductSearchResult result = productService.search("1781417-68-2", null, 10);

        assertThat(result.items()).extracting(ProductSummary::catNo).contains("BA044");
        assertThat(result.items().getFirst().casNo()).isEqualTo("1781417-68-2");
    }

    @Test
    void productDetailsForBa044() {
        ProductDetail detail = productService.findByCatNo("BA044").orElseThrow();

        assertThat(detail.name()).isEqualTo("Salbutamol-D9 acetate");
        assertThat(detail.category()).isEqualTo("Stable Isotopes");
        assertThat(detail.casNo()).isEqualTo("1781417-68-2");
        assertThat(detail.molecularFormula()).isNotBlank();
        assertThat(detail.stockStatus()).isNotBlank();
        assertThat(detail.url()).isEqualTo("/product/ba044");
    }

    @Test
    void categoriesExcludeConfiguredOnes() {
        List<CategoryInfo> categories = catalogueService.categories();

        assertThat(categories).isNotEmpty();
        assertThat(categories).extracting(CategoryInfo::name)
                .contains("Impurity", "Stable Isotopes")
                .doesNotContainAnyElementsOf(catalogue.excludedCategories());
    }

    @Test
    void pricingQueryRunsForBothCountryRules() {
        PricingResult india = pricingService.getPricing("BA044", "IN");
        PricingResult abroad = pricingService.getPricing("BA044", "US");

        assertThat(india.catNo()).isEqualTo("BA044");
        assertThat(india.countryCode()).isEqualTo("IN");
        assertThat(abroad.countryCode()).isEqualTo("US");
        assertThat(india.status()).isNotNull();
        assertThat(abroad.status()).isNotNull();
    }

    /** KL-02-34320 is a website-visible product in "Items", a category hidden from browsing only. */
    @Test
    void browseOnlyExclusionsStayFindableBySearchAndLookup() {
        assertThat(productService.findByCatNo("KL-02-34320")).get()
                .extracting(ProductDetail::category).isEqualTo("Items");
        assertThat(productService.search("KL-02-34320", null, 10).items())
                .extracting(ProductSummary::catNo).contains("KL-02-34320");
        assertThat(catalogueService.browse("Items", null, null, 10).total()).isZero();
    }

    @Test
    void loginCannotReadOtherTables() {
        assertThatThrownBy(() -> jdbcTemplate.queryForObject("SELECT TOP 1 DocEntry FROM OINV", Integer.class))
                .isInstanceOf(DataAccessException.class)
                .satisfies(e -> {
                    Throwable root = NestedExceptionUtils.getMostSpecificCause(e);
                    assertThat(root).isInstanceOf(SQLException.class);
                    assertThat(((SQLException) root).getErrorCode()).isEqualTo(PERMISSION_DENIED);
                    assertThat(root.getMessage()).containsIgnoringCase("permission");
                });
    }
}
