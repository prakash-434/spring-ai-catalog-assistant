package com.kaarislabs.chatbot.service;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import com.kaarislabs.chatbot.common.InputValidator;
import com.kaarislabs.chatbot.common.InvalidInputException;
import com.kaarislabs.chatbot.config.CatalogueProperties;
import com.kaarislabs.chatbot.repository.CategoryRepository;
import com.kaarislabs.chatbot.repository.ProductRepository;
import com.kaarislabs.chatbot.repository.StockStatusFilter;
import com.kaarislabs.chatbot.repository.row.NameCount;
import com.kaarislabs.chatbot.repository.row.Page;
import com.kaarislabs.chatbot.repository.row.ProductRow;
import com.kaarislabs.chatbot.service.dto.ApiFamilyInfo;
import com.kaarislabs.chatbot.service.dto.ApiFamilyList;
import com.kaarislabs.chatbot.service.dto.BrowseResult;
import com.kaarislabs.chatbot.service.dto.CategoryInfo;

/** Category → API family → product navigation. */
@Service
public class CatalogueService {

    static final int DEFAULT_LIMIT = 20;
    static final int MAX_LIMIT = 50;

    private final CategoryRepository categories;
    private final ProductRepository products;
    private final CatalogueProperties catalogue;

    public CatalogueService(CategoryRepository categories, ProductRepository products, CatalogueProperties catalogue) {
        this.categories = categories;
        this.products = products;
        this.catalogue = catalogue;
    }

    @Cacheable("categories")
    public List<CategoryInfo> categories() {
        return categories.findAllWithCounts().stream()
                .map(c -> new CategoryInfo(c.name(), c.productCount(), "/categories/" + categorySlug(c.name())))
                .toList();
    }

    /**
     * @param category optional category name; null lists families across all categories
     * @param query    optional text the family name must contain
     */
    @Cacheable("apiFamilies")
    public ApiFamilyList apiFamilies(String category, String query, int limit) {
        String cat = InputValidator.optionalName(category, "category");
        String q = InputValidator.optionalName(query, "API family");
        if (catalogue.isExcluded(cat)) {
            return new ApiFamilyList(cat, 0, List.of());
        }
        Page<NameCount> page = categories.findApiFamilies(cat, q, InputValidator.clampLimit(limit, DEFAULT_LIMIT, MAX_LIMIT));
        return new ApiFamilyList(cat, page.total(),
                page.items().stream().map(f -> new ApiFamilyInfo(f.name(), f.productCount())).toList());
    }

    /**
     * @param stockStatus null/"all", a website key such as "in_stock", or a stored label such as "In Stock"
     */
    public BrowseResult browse(String category, String apiFamily, String stockStatus, int limit) {
        String cat = InputValidator.optionalName(category, "category");
        String family = InputValidator.optionalName(apiFamily, "API family");
        StockStatusFilter stock = parseStock(stockStatus);
        String stockLabel = stock == null ? null : stock.label();
        if (catalogue.isExcluded(cat)) {
            return new BrowseResult(cat, family, stockLabel, 0, List.of());
        }
        Page<ProductRow> page = products.browse(cat, family, stock, InputValidator.clampLimit(limit, DEFAULT_LIMIT, MAX_LIMIT));
        return new BrowseResult(cat, family, stockLabel, page.total(),
                page.items().stream().map(ProductService::toSummary).toList());
    }

    /** Port of generateCategorySlug() from the website, so links match /categories/{slug}. */
    static String categorySlug(String category) {
        return category.toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", "-")
                .replace("&", "and")
                .replaceAll("[^a-z0-9-]", "")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");
    }

    private static StockStatusFilter parseStock(String value) {
        if (value == null || value.isBlank() || value.trim().equalsIgnoreCase("all")) {
            return null;
        }
        return StockStatusFilter.parse(value).orElseThrow(() -> new InvalidInputException(
                "Unknown stock status. Use one of: all, " + Arrays.stream(StockStatusFilter.values())
                        .map(StockStatusFilter::key).collect(Collectors.joining(", ")) + "."));
    }
}
