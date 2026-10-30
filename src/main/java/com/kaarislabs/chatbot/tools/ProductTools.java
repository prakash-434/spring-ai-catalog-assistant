package com.kaarislabs.chatbot.tools;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import com.kaarislabs.chatbot.chat.ChatAction;
import com.kaarislabs.chatbot.chat.ChatContext;
import com.kaarislabs.chatbot.common.InputValidator;
import com.kaarislabs.chatbot.service.CatalogueService;
import com.kaarislabs.chatbot.service.ProductService;
import com.kaarislabs.chatbot.service.dto.BrowseResult;
import com.kaarislabs.chatbot.service.dto.ProductSearchResult;
import com.kaarislabs.chatbot.service.dto.ProductSummary;
import com.kaarislabs.chatbot.tools.ToolResults.Category;
import com.kaarislabs.chatbot.tools.ToolResults.NotFound;
import com.kaarislabs.chatbot.tools.ToolResults.ProductHit;
import com.kaarislabs.chatbot.tools.ToolResults.ProductInfo;
import com.kaarislabs.chatbot.tools.ToolResults.ProductList;
import com.kaarislabs.chatbot.tools.ToolSupport.ToolError;

/** Product search, details and category navigation for the model. */
@Component
public class ProductTools {

    private static final Logger log = LoggerFactory.getLogger(ProductTools.class);

    static final int MAX_PRODUCTS = 5;
    static final int MAX_FAMILIES = 20;

    private final ProductService productService;
    private final CatalogueService catalogueService;

    public ProductTools(ProductService productService, CatalogueService catalogueService) {
        this.productService = productService;
        this.catalogueService = catalogueService;
    }

    @Tool(name = "searchProducts", description = """
            Search the Kaaris Labs catalogue by product name, catalogue number (Cat No, e.g. BA044 or KL-13-00645), \
            CAS number (e.g. 1781417-68-2), IUPAC name or synonym. Use this first whenever the customer names, \
            describes or gives a code for a product. Returns the best matches first (at most 5) and the total number \
            of matches, each with catNo, name, category, apiFamily, casNo, molecularFormula, molecularWeight and \
            stockStatus.""")
    public Object searchProducts(
            @ToolParam(description = "What to search for: a product name, Cat No, CAS number or synonym, as the "
                    + "customer wrote it. Do not add words such as 'impurity' or 'standard'.") String query,
            @ToolParam(required = false, description = "Exact category name from listCategories to search "
                    + "within, e.g. 'Impurity'. Omit to search every category.") String category,
            @ToolParam(required = false, description = "Maximum number of results, 1 to 5. Default 5.") Integer limit,
            ToolContext toolContext) {
        ChatContext context = ChatContext.from(toolContext);
        return ToolSupport.run(context, "searchProducts", () -> {
            String q = InputValidator.requireSearchQuery(query);
            ProductSearchResult result = productService.search(q, category, productLimit(limit));
            if (result.total() == 0) {
                log.info("Search with no results: query=\"{}\" category=\"{}\"", q, category);
            }
            return productList(context, result.total(), result.items());
        });
    }

    @Tool(name = "getProductDetails", description = """
            Full details of one product by its exact Cat No: name, CAS number, alternative CAS, molecular formula, \
            molecular weight, stock status, IUPAC name, synonyms, SMILES, solvent, stable isotope type, storage \
            temperature, product format, purity and shipping temperature. Use it for questions about a specific \
            property. If the Cat No is not known, use searchProducts first.""")
    public Object getProductDetails(
            @ToolParam(description = "The product's Cat No, e.g. BA044.") String catNo,
            ToolContext toolContext) {
        ChatContext context = ChatContext.from(toolContext);
        return ToolSupport.run(context, "getProductDetails", () -> {
            String code = InputValidator.requireCatNo(catNo);
            return productService.findByCatNo(code)
                    .<Object>map(p -> {
                        context.addAction(ChatAction.product(p.catNo()));
                        context.addAction(ChatAction.quote(p.catNo()));
                        return ProductInfo.of(p);
                    })
                    .orElseGet(() -> NotFound.of("No product has Cat No " + code + ". Try searchProducts."));
        });
    }

    @Tool(name = "listCategories", description = """
            List the product categories customers can browse (for example Impurity or Stable Isotopes), each with \
            its number of products.""")
    public Object listCategories(ToolContext toolContext) {
        return ToolSupport.run(ChatContext.from(toolContext), "listCategories", () -> catalogueService.categories().stream()
                .map(c -> new Category(c.name(), c.productCount()))
                .toList());
    }

    @Tool(name = "listApiFamilies", description = """
            List the API families (the parent drug a product belongs to, e.g. Famotidine) in one category, with \
            product counts, largest first. Use it to find the exact family name before browseProducts. Example: for \
            'impurities of Famotidine' call it with category 'Impurity' and query 'Famotidine'.""")
    public Object listApiFamilies(
            @ToolParam(description = "Exact category name from listCategories, e.g. 'Impurity'.") String category,
            @ToolParam(required = false, description = "Text the family name must contain, e.g. 'Famotidine'. "
                    + "Omit to list the largest families.") String query,
            ToolContext toolContext) {
        return ToolSupport.run(ChatContext.from(toolContext), "listApiFamilies", () -> {
            String cat = requireCategory(category);
            if (cat == null) {
                return new ToolError("A category is required. Call listCategories to see the names.");
            }
            return catalogueService.apiFamilies(cat, query, MAX_FAMILIES);
        });
    }

    @Tool(name = "browseProducts", description = """
            List products in one category, optionally narrowed to one API family and a stock status. Returns at \
            most 5 products ordered by name, and the total number that match. Use exact names from listCategories \
            and listApiFamilies.""")
    public Object browseProducts(
            @ToolParam(description = "Exact category name from listCategories, e.g. 'Impurity'.") String category,
            @ToolParam(required = false, description = "Exact API family name from listApiFamilies, e.g. "
                    + "'Famotidine'.") String apiFamily,
            @ToolParam(required = false, description = "Only products with this stock status. One of: in_stock, "
                    + "limited_stock, in_stock_2_3_days, ongoing_in_lab, in_qc_analysis, synthesis_on_demand. Omit "
                    + "for all.") String stockStatus,
            @ToolParam(required = false, description = "Maximum number of results, 1 to 5. Default 5.") Integer limit,
            ToolContext toolContext) {
        ChatContext context = ChatContext.from(toolContext);
        return ToolSupport.run(context, "browseProducts", () -> {
            String cat = requireCategory(category);
            if (cat == null) {
                return new ToolError("A category is required. Call listCategories to see the names.");
            }
            BrowseResult result = catalogueService.browse(cat, apiFamily, stockStatus, productLimit(limit));
            return productList(context, result.total(), result.items());
        });
    }

    private static ProductList productList(ChatContext context, long total, List<ProductSummary> items) {
        items.forEach(p -> context.addAction(ChatAction.product(p.catNo())));
        return new ProductList(total, items.stream().map(ProductHit::of).toList());
    }

    private static int productLimit(Integer limit) {
        return InputValidator.clampLimit(limit == null ? 0 : limit, MAX_PRODUCTS, MAX_PRODUCTS);
    }

    private static String requireCategory(String category) {
        return InputValidator.optionalName(category, "category");
    }
}
