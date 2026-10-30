package com.kaarislabs.chatbot.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Currency;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;

import org.springframework.stereotype.Service;

import com.kaarislabs.chatbot.common.InputValidator;
import com.kaarislabs.chatbot.repository.PricingRepository;
import com.kaarislabs.chatbot.repository.row.PackRow;
import com.kaarislabs.chatbot.service.dto.PackSizePrice;
import com.kaarislabs.chatbot.service.dto.PricingResult;
import com.kaarislabs.chatbot.service.dto.PricingStatus;

/**
 * Applies the website's price visibility rule (components/productdetail/AvailablePackSizes.tsx) on the server:
 * <ul>
 * <li>no pack rows → CONTACT_FOR_QUOTE</li>
 * <li>unknown country → treated as India (the website's fallback)</li>
 * <li>India and U_IndiaPriceDisplay ≠ 'Yes' → CONTACT_FOR_PRICING, pack sizes without prices</li>
 * <li>India and 'Yes' → INR per pack, "contact" where the price is ≤ 0, plus the GST note</li>
 * <li>elsewhere → USD per pack, "contact" where the price is ≤ 0</li>
 * </ul>
 * One currency per result and never a fallback to the other one. A raw price that may not be shown never leaves this
 * class.
 */
@Service
public class PricingService {

    static final String INDIA = "IN";
    static final String GST_NOTE = "18% GST extra";
    static final String CONTACT = "contact";

    private static final Locale INDIA_LOCALE = Locale.of("en", "IN");

    private final PricingRepository pricing;

    public PricingService(PricingRepository pricing) {
        this.pricing = pricing;
    }

    /**
     * @param countryCode ISO-3166 alpha-2 country of the visitor, as detected from their IP; null or unrecognised
     *                    values are treated as "IN"
     */
    public PricingResult getPricing(String catNo, String countryCode) {
        String code = InputValidator.requireCatNo(catNo);
        String country = normalizeCountry(countryCode);
        List<PackRow> rows = dedupe(pricing.findPackRows(code));

        if (rows.isEmpty()) {
            return new PricingResult(code, country, PricingStatus.CONTACT_FOR_QUOTE, null, List.of(), null);
        }

        if (INDIA.equals(country)) {
            boolean anyIndiaPriced = rows.stream().anyMatch(PricingService::indiaPriceAllowed);
            if (!anyIndiaPriced) {
                List<PackSizePrice> packs = rows.stream().map(r -> pack(r, null, null)).toList();
                return new PricingResult(code, country, PricingStatus.CONTACT_FOR_PRICING, null, packs, null);
            }
            // The flag is per item, so a variant item without the flag still reads "contact".
            List<PackSizePrice> packs = priced(rows, r -> indiaPriceAllowed(r) ? r.priceInr() : null, "INR");
            return new PricingResult(code, country, PricingStatus.PRICED, "INR", packs, GST_NOTE);
        }

        List<PackSizePrice> packs = priced(rows, PackRow::priceUsd, "USD");
        return new PricingResult(code, country, PricingStatus.PRICED, "USD", packs, null);
    }

    static String normalizeCountry(String countryCode) {
        if (countryCode == null) {
            return INDIA;
        }
        String c = countryCode.trim().toUpperCase(Locale.ROOT);
        return c.matches("[A-Z]{2}") ? c : INDIA;
    }

    static boolean indiaPriceAllowed(PackRow row) {
        return row.indiaPriceDisplay() != null && row.indiaPriceDisplay().trim().equalsIgnoreCase("Yes");
    }

    /** Same pack label as the website's formatPackSize(): "25 mg", "2.5 g". */
    static String packLabel(BigDecimal quantity, String uom) {
        BigDecimal q = quantity.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros();
        return q.toPlainString() + " " + (uom == null ? "" : uom.trim().toLowerCase(Locale.ROOT));
    }

    static String format(BigDecimal price, String currency) {
        NumberFormat format = NumberFormat.getCurrencyInstance("INR".equals(currency) ? INDIA_LOCALE : Locale.US);
        format.setCurrency(Currency.getInstance(currency));
        format.setMinimumFractionDigits(2);
        return format.format(price);
    }

    private static List<PackSizePrice> priced(List<PackRow> rows, Function<PackRow, BigDecimal> priceOf, String currency) {
        List<PackSizePrice> packs = new ArrayList<>();
        for (PackRow row : rows) {
            BigDecimal price = priceOf.apply(row);
            if (price != null && price.signum() > 0) {
                packs.add(pack(row, price, format(price, currency)));
            } else {
                packs.add(pack(row, null, CONTACT));
            }
        }
        return packs;
    }

    private static PackSizePrice pack(PackRow row, BigDecimal price, String display) {
        String uom = row.uom() == null ? null : row.uom().trim();
        return new PackSizePrice(row.itemCode(), packLabel(row.quantity(), uom), row.quantity(), uom, price, display);
    }

    /** One entry per item code + pack size (the pricing tables can repeat a pack). */
    private static List<PackRow> dedupe(List<PackRow> rows) {
        Set<String> seen = new HashSet<>();
        List<PackRow> unique = new ArrayList<>();
        for (PackRow row : rows) {
            if (row.quantity() == null) {
                continue;
            }
            String key = row.itemCode() + "|" + packLabel(row.quantity(), row.uom());
            if (seen.add(key)) {
                unique.add(row);
            }
        }
        return unique;
    }
}
