package com.kaarislabs.chatbot.service;

import static com.kaarislabs.chatbot.common.TextCleaner.clean;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.kaarislabs.chatbot.common.InputValidator;
import com.kaarislabs.chatbot.common.TextCleaner;
import com.kaarislabs.chatbot.config.CatalogueProperties;
import com.kaarislabs.chatbot.repository.ProductRepository;
import com.kaarislabs.chatbot.repository.row.Page;
import com.kaarislabs.chatbot.repository.row.ProductRow;
import com.kaarislabs.chatbot.service.dto.ProductDetail;
import com.kaarislabs.chatbot.service.dto.ProductSearchResult;
import com.kaarislabs.chatbot.service.dto.ProductSummary;

/** Product search and details, cleaned for customers. */
@Service
public class ProductService {

    static final int DEFAULT_SEARCH_LIMIT = 10;
    static final int MAX_SEARCH_LIMIT = 50;

    private final ProductRepository products;
    private final CatalogueProperties catalogue;

    public ProductService(ProductRepository products, CatalogueProperties catalogue) {
        this.products = products;
        this.catalogue = catalogue;
    }

    /**
     * Searches by name, catalogue number, CAS, IUPAC name or synonym.
     *
     * @param category optional category name to search within
     */
    public ProductSearchResult search(String query, String category, int limit) {
        String q = InputValidator.requireSearchQuery(query);
        String cat = InputValidator.optionalName(category, "category");
        if (catalogue.isNonProduct(cat)) {
            return new ProductSearchResult(q, 0, List.of());
        }
        int max = InputValidator.clampLimit(limit, DEFAULT_SEARCH_LIMIT, MAX_SEARCH_LIMIT);
        Page<ProductRow> page = products.search(q, cat, max);
        return new ProductSearchResult(q, page.total(), page.items().stream().map(ProductService::toSummary).toList());
    }

    public Optional<ProductDetail> findByCatNo(String catNo) {
        String code = InputValidator.requireCatNo(catNo);
        return products.findByCatNo(code).map(ProductService::toDetail);
    }

    static ProductSummary toSummary(ProductRow row) {
        return new ProductSummary(
                row.itemCode(),
                displayName(row),
                clean(row.category()),
                clean(row.apiFamily()),
                validCas(row.casNo()),
                clean(row.molecularFormula()),
                clean(row.molecularWeight()),
                stockLabel(row.inventoryStatus()),
                clean(row.brand()),
                productUrl(row.itemCode()));
    }

    static ProductDetail toDetail(ProductRow row) {
        return new ProductDetail(
                row.itemCode(),
                displayName(row),
                clean(row.category()),
                clean(row.apiFamily()),
                validCas(row.casNo()),
                validCas(row.altCasNo()),
                clean(row.molecularFormula()),
                clean(row.molecularWeight()),
                stockLabel(row.inventoryStatus()),
                clean(row.iupacName()),
                clean(row.synonyms()),
                clean(row.smiles()),
                clean(row.solvent()),
                clean(row.stableIsotopesType()),
                clean(row.storageTemp()),
                clean(row.productFormat()),
                clean(row.purity()),
                clean(row.shippingTemp()),
                clean(row.brand()),
                productUrl(row.itemCode()));
    }

    /** Same canonical URL as the website: /product/{lower-case catNo}. */
    static String productUrl(String itemCode) {
        return "/product/" + itemCode.toLowerCase(Locale.ROOT);
    }

    private static String displayName(ProductRow row) {
        String name = clean(row.itemName());
        return name != null ? name : row.itemCode();
    }

    private static String validCas(String cas) {
        return TextCleaner.isValidCas(cas) ? clean(cas) : null;
    }

    /** The stock label exactly as stored; only a missing or blank value becomes null. */
    private static String stockLabel(String status) {
        return status == null || status.isBlank() ? null : status;
    }
}
