package com.kaarislabs.chatbot.guard;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.ai.tool.execution.DefaultToolCallResultConverter;
import org.springframework.stereotype.Component;

import com.kaarislabs.chatbot.chat.ChatContext;
import com.kaarislabs.chatbot.chat.OutputGuard;
import com.kaarislabs.chatbot.tools.ToolResults;

/**
 * Blocks replies that state a price, percentage or CAS number the tools did not return in this turn:
 * <ul>
 * <li>currency amounts ($, USD, ₹, INR, Rs) must equal a pack price of a PRICED pricing result, compared as numbers
 * ("$1,055.00" = "1055 USD"); with no PRICED result in the turn, no amount is allowed at all;</li>
 * <li>percentages ("98%", "&gt; 95%", "98.0 atom%") must occur in the tool results;</li>
 * <li>CAS-shaped strings must occur in the tool results or in the customer's own message (so "we have nothing for
 * CAS 123-45-6" is not blocked).</li>
 * </ul>
 * Other numbers, such as molecular weights, are not checked.
 */
@Component
public class ToolResultOutputGuard implements OutputGuard {

    // 1055, 1055.00, 1,055.00 and Indian grouping 1,23,456.00
    private static final String AMOUNT = "(\\d{1,3}(?:,\\d{2,3})+(?:\\.\\d+)?|\\d+(?:\\.\\d+)?)";
    private static final String CODE = "(?:US\\$|\\$|₹|(?<![A-Za-z])(?:USD|INR|Rs\\.?)(?![A-Za-z]))";
    private static final Pattern AMOUNT_AFTER_CURRENCY = Pattern.compile(CODE + "\\s*" + AMOUNT,
            Pattern.CASE_INSENSITIVE);
    // The look-behind stops "BA072 $345" from reading as the amount "072" followed by "$".
    private static final Pattern AMOUNT_BEFORE_CURRENCY = Pattern.compile(
            "(?<![A-Za-z0-9.,])" + AMOUNT + "\\s*(?:\\$|₹|(?:USD|INR|Rs\\.?|dollars?|rupees?)(?![A-Za-z]))",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern NUMBER = Pattern.compile(AMOUNT);
    private static final Pattern PERCENT = Pattern.compile("(?<![\\d.])(\\d+(?:\\.\\d+)?)\\s*(?:atom\\s*)?%",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern CAS = Pattern.compile("(?<![\\d-])\\d{2,7}-\\d{2}-\\d(?![\\d-])");

    private static final DefaultToolCallResultConverter CONVERTER = new DefaultToolCallResultConverter();

    @Override
    public Optional<Violation> check(String reply, String userMessage, ChatContext context) {
        List<Object> results = context.toolResults();
        String toolText = results.stream()
                .map(r -> CONVERTER.convert(r, r.getClass()))
                .collect(Collectors.joining("\n"));

        return checkPrices(reply, results)
                .or(() -> checkPercentages(reply, toolText))
                .or(() -> checkCas(reply, toolText, userMessage == null ? "" : userMessage));
    }

    private static Optional<Violation> checkPrices(String reply, List<Object> results) {
        List<String> amounts = currencyAmounts(reply);
        if (amounts.isEmpty()) {
            return Optional.empty();
        }
        Set<BigDecimal> allowed = new HashSet<>();
        for (Object r : results) {
            if (r instanceof ToolResults.Pricing p && "PRICED".equals(p.status())) {
                p.packSizes().forEach(pack -> number(pack.price()).ifPresent(allowed::add));
            }
        }
        if (allowed.isEmpty()) {
            return Optional.of(new Violation("price_without_pricing", amounts.getFirst()));
        }
        for (String amount : amounts) {
            if (number(amount).filter(allowed::contains).isEmpty()) {
                return Optional.of(new Violation("unknown_price", amount));
            }
        }
        return Optional.empty();
    }

    private static Optional<Violation> checkPercentages(String reply, String toolText) {
        Set<BigDecimal> allowed = new HashSet<>();
        Matcher known = PERCENT.matcher(toolText);
        while (known.find()) {
            allowed.add(normalize(known.group(1)));
        }
        Matcher m = PERCENT.matcher(reply);
        while (m.find()) {
            if (!allowed.contains(normalize(m.group(1)))) {
                return Optional.of(new Violation("unknown_percentage", m.group()));
            }
        }
        return Optional.empty();
    }

    private static Optional<Violation> checkCas(String reply, String toolText, String userMessage) {
        Matcher m = CAS.matcher(reply);
        while (m.find()) {
            String cas = m.group();
            if (!toolText.contains(cas) && !userMessage.contains(cas)) {
                return Optional.of(new Violation("unknown_cas", cas));
            }
        }
        return Optional.empty();
    }

    /** Every currency amount in the text, as written (e.g. "$1,055.00", "1055 USD"). */
    public static List<String> currencyAmounts(String text) {
        List<String> found = new ArrayList<>();
        Matcher before = AMOUNT_AFTER_CURRENCY.matcher(text);
        while (before.find()) {
            found.add(before.group());
        }
        Matcher after = AMOUNT_BEFORE_CURRENCY.matcher(text);
        while (after.find()) {
            found.add(after.group());
        }
        return found;
    }

    /** The numeric value of a price text such as "$1,055.00" or "₹1,23,456"; empty for "contact" or null. */
    private static Optional<BigDecimal> number(String text) {
        if (text == null) {
            return Optional.empty();
        }
        Matcher m = NUMBER.matcher(text);
        return m.find() ? Optional.of(normalize(m.group(1))) : Optional.empty();
    }

    private static BigDecimal normalize(String digits) {
        return new BigDecimal(digits.replace(",", "")).stripTrailingZeros();
    }
}
