package com.kaarislabs.chatbot.dev;

import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.kaarislabs.chatbot.service.CatalogueService;
import com.kaarislabs.chatbot.service.DocumentService;
import com.kaarislabs.chatbot.service.PricingService;
import com.kaarislabs.chatbot.service.ProductService;
import com.kaarislabs.chatbot.service.dto.ApiFamilyList;
import com.kaarislabs.chatbot.service.dto.BrowseResult;
import com.kaarislabs.chatbot.service.dto.CategoryInfo;
import com.kaarislabs.chatbot.service.dto.DocumentAvailability;
import com.kaarislabs.chatbot.service.dto.ProductDetail;
import com.kaarislabs.chatbot.service.dto.ProductSearchResult;
import com.kaarislabs.chatbot.service.dto.PricingResult;

/**
 * Browser test endpoints for the data layer. Registered only with the "dev" profile, so they do not exist in any
 * other environment.
 */
@Profile("dev")
@RestController
@RequestMapping("/dev")
public class DevController {

    private final ProductService productService;
    private final CatalogueService catalogueService;
    private final PricingService pricingService;
    private final DocumentService documentService;

    public DevController(ProductService productService, CatalogueService catalogueService,
            PricingService pricingService, DocumentService documentService) {
        this.productService = productService;
        this.catalogueService = catalogueService;
        this.pricingService = pricingService;
        this.documentService = documentService;
    }

    @GetMapping("/search")
    public ProductSearchResult search(@RequestParam String q,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "10") int limit) {
        return productService.search(q, category, limit);
    }

    @GetMapping("/product/{catNo}")
    public ResponseEntity<ProductDetail> product(@PathVariable String catNo) {
        return ResponseEntity.of(productService.findByCatNo(catNo));
    }

    @GetMapping("/categories")
    public List<CategoryInfo> categories() {
        return catalogueService.categories();
    }

    @GetMapping("/families")
    public ApiFamilyList families(@RequestParam(required = false) String category,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "20") int limit) {
        return catalogueService.apiFamilies(category, q, limit);
    }

    @GetMapping("/browse")
    public BrowseResult browse(@RequestParam(required = false) String category,
            @RequestParam(required = false) String apiFamily,
            @RequestParam(required = false) String stock,
            @RequestParam(defaultValue = "20") int limit) {
        return catalogueService.browse(category, apiFamily, stock, limit);
    }

    @GetMapping("/pricing/{catNo}")
    public PricingResult pricing(@PathVariable String catNo, @RequestParam(required = false) String country) {
        return pricingService.getPricing(catNo, country);
    }

    @GetMapping("/documents")
    public DocumentAvailability documents(@RequestParam String itemCode, @RequestParam String batch) {
        return documentService.checkDocuments(itemCode, batch);
    }
}
