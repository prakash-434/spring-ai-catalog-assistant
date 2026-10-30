package com.kaarislabs.chatbot.fallback;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.kaarislabs.chatbot.chat.ChatAction;
import com.kaarislabs.chatbot.common.InputValidator;
import com.kaarislabs.chatbot.common.InvalidInputException;
import com.kaarislabs.chatbot.config.CompanyProperties;
import com.kaarislabs.chatbot.service.PricingService;
import com.kaarislabs.chatbot.service.ProductService;
import com.kaarislabs.chatbot.service.dto.PricingStatus;
import com.kaarislabs.chatbot.service.dto.ProductDetail;
import com.kaarislabs.chatbot.service.dto.ProductSearchResult;
import com.kaarislabs.chatbot.service.dto.ProductSummary;
import com.kaarislabs.chatbot.stats.ChatStats;

/**
 * Templated answers without the model, used when it must not or cannot be called. Product facts come straight from
 * the services; prices are never written, only whether the product page shows them.
 */
@Component
public class FallbackResponder {

    private static final Logger log = LoggerFactory.getLogger(FallbackResponder.class);

    static final int MAX_ITEMS = 5;
    static final int MAX_CAT_NO_CANDIDATES = 3;
    static final String NOTHING_FOUND =
            "I couldn't find that right now. Please contact sales@kaarislabs.com or request a quote.";
    static final String PRICES_ON_PAGE = "Pack sizes and prices are shown on the product page.";
    static final String REQUEST_QUOTE = "Please request a quote.";

    private static final Pattern CAS = Pattern.compile("(?<![\\d-])\\d{2,7}-\\d{2}-\\d(?![\\d-])");
    private static final Pattern WORD_SPLIT = Pattern.compile("[^A-Za-z0-9.\\-]+");
    private static final Pattern HAS_LETTER = Pattern.compile("[A-Za-z]");
    private static final Pattern HAS_DIGIT = Pattern.compile("\\d");
    // Words that only make a free-text search fail (the search requires every word to match).
    private static final Set<String> STOP_WORDS = Set.of("a", "an", "the", "do", "does", "you", "have", "has", "i",
            "we", "need", "want", "looking", "look", "for", "of", "is", "are", "any", "me", "show", "find", "please",
            "can", "could", "with", "and", "or", "to", "what", "which", "your", "my", "about", "in", "on", "get",
            "give", "tell", "price", "prices", "cost", "available", "availability", "stock", "there", "hi", "hello",
            "it", "its", "this", "that", "list", "some", "how", "much", "many");

    /** A fallback answer: text without any price, and its buttons. */
    public record Answer(String text, List<ChatAction> actions) {
    }

    private record Item(String catNo, String name, String casNo, String stockStatus) {

        static Item of(ProductDetail p) {
            return new Item(p.catNo(), p.name(), p.casNo(), p.stockStatus());
        }

        static Item of(ProductSummary p) {
            return new Item(p.catNo(), p.name(), p.casNo(), p.stockStatus());
        }
    }

    private final ProductService products;
    private final PricingService pricing;
    private final CompanyProperties company;
    private final ChatStats stats;

    public FallbackResponder(ProductService products, PricingService pricing, CompanyProperties company,
            ChatStats stats) {
        this.products = products;
        this.pricing = pricing;
        this.company = company;
        this.stats = stats;
    }

    /** @param countryCode decides only which pricing sentence is used; prices themselves are never shown */
    public Answer answer(String message, String countryCode) {
        Optional<String> cas = casNumber(message);
        if (cas.isPresent()) {
            return byCas(cas.get(), countryCode);
        }
        for (String candidate : catNoCandidates(message)) {
            Optional<ProductDetail> product = products.findByCatNo(candidate);
            if (product.isPresent()) {
                Item item = Item.of(product.get());
                return new Answer(summary(item, countryCode), productActions(List.of(item)));
            }
        }
        return bySearch(message);
    }

    private Answer byCas(String cas, String countryCode) {
        ProductSearchResult result = products.search(cas, null, MAX_ITEMS);
        if (result.items().isEmpty()) {
            stats.zeroResultSearch();
            return nothingFound();
        }
        List<Item> items = result.items().stream().map(Item::of).toList();
        String intro = items.size() == 1 ? "" : "I found " + result.total() + " products for CAS " + cas + ".\n\n";
        String body = items.stream().map(i -> summary(i, countryCode)).collect(Collectors.joining("\n\n"));
        return new Answer(intro + body, productActions(items));
    }

    private Answer bySearch(String message) {
        String query = Arrays.stream(WORD_SPLIT.split(message.toLowerCase(Locale.ROOT)))
                .map(w -> w.replaceAll("^[.\\-]+|[.\\-]+$", ""))
                .filter(w -> !w.isEmpty() && !STOP_WORDS.contains(w))
                .collect(Collectors.joining(" "));
        if (query.isEmpty()) {
            return nothingFound();
        }
        if (query.length() > InputValidator.MAX_QUERY_LENGTH) {
            query = query.substring(0, InputValidator.MAX_QUERY_LENGTH).strip();
        }
        ProductSearchResult result = products.search(query, null, MAX_ITEMS);
        if (result.items().isEmpty()) {
            log.info("Search with no results (fallback): query=\"{}\"", query);
            stats.zeroResultSearch();
            return nothingFound();
        }
        List<Item> items = result.items().stream().map(Item::of).toList();
        StringBuilder text = new StringBuilder("Here are the closest matches I found:\n");
        for (Item i : items) {
            text.append("\n- ").append(i.catNo()).append(": ").append(i.name());
            if (i.stockStatus() != null) {
                text.append(" (").append(i.stockStatus()).append(')');
            }
        }
        text.append("\n\n").append(result.total()).append(result.total() == 1 ? " product" : " products")
                .append(" found in total.");
        return new Answer(text.toString(), productActions(items));
    }

    private String summary(Item item, String countryCode) {
        StringBuilder text = new StringBuilder(item.name()).append(" (Cat No ").append(item.catNo()).append(')');
        if (item.casNo() != null) {
            text.append("\nCAS: ").append(item.casNo());
        }
        if (item.stockStatus() != null) {
            text.append("\nStock: ").append(item.stockStatus());
        }
        boolean priced = pricing.getPricing(item.catNo(), countryCode).status() == PricingStatus.PRICED;
        return text.append('\n').append(priced ? PRICES_ON_PAGE : REQUEST_QUOTE).toString();
    }

    private Answer nothingFound() {
        Set<ChatAction> actions = new LinkedHashSet<>();
        CompanyProperties.Links links = company.links();
        if (links != null && links.quote() != null) {
            actions.add(ChatAction.page(links.quote()));
        }
        actions.add(ChatAction.quote(null));
        return new Answer(NOTHING_FOUND, List.copyOf(actions));
    }

    private static List<ChatAction> productActions(List<Item> items) {
        List<ChatAction> actions = new ArrayList<>();
        for (Item i : items) {
            actions.add(ChatAction.product(i.catNo()));
        }
        if (items.size() == 1) {
            actions.add(ChatAction.quote(items.getFirst().catNo()));
        }
        return actions;
    }

    static Optional<String> casNumber(String message) {
        Matcher m = CAS.matcher(message);
        while (m.find()) {
            if (InputValidator.isCasNumber(m.group())) {
                return Optional.of(m.group());
            }
        }
        return Optional.empty();
    }

    /** Words shaped like a Cat No / item code: letters and digits, valid for the Cat No validator. */
    static List<String> catNoCandidates(String message) {
        List<String> candidates = new ArrayList<>();
        for (String raw : WORD_SPLIT.split(message)) {
            String word = raw.replaceAll("^[.\\-]+|[.\\-]+$", "");
            if (word.length() < 3 || !HAS_LETTER.matcher(word).find() || !HAS_DIGIT.matcher(word).find()) {
                continue;
            }
            try {
                candidates.add(InputValidator.requireCatNo(word).toUpperCase(Locale.ROOT));
            } catch (InvalidInputException e) {
                continue;
            }
            if (candidates.size() == MAX_CAT_NO_CANDIDATES) {
                break;
            }
        }
        return candidates;
    }
}
