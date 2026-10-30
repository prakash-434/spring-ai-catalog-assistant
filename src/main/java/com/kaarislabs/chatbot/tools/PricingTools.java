package com.kaarislabs.chatbot.tools;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import com.kaarislabs.chatbot.chat.ChatAction;
import com.kaarislabs.chatbot.chat.ChatContext;
import com.kaarislabs.chatbot.common.InputValidator;
import com.kaarislabs.chatbot.service.PricingService;
import com.kaarislabs.chatbot.service.dto.PricingResult;
import com.kaarislabs.chatbot.tools.ToolResults.Pack;
import com.kaarislabs.chatbot.tools.ToolResults.Pricing;

/** Pack sizes and prices. The visitor's country comes from the request context, never from the model. */
@Component
public class PricingTools {

    private final PricingService pricingService;

    public PricingTools(PricingService pricingService) {
        this.pricingService = pricingService;
    }

    @Tool(name = "getPackSizesAndPrice", description = """
            Pack sizes and prices of one product by Cat No. The customer's country is applied automatically. The \
            status tells you what to say: PRICED = state the pack sizes with the given prices in the given currency \
            (a price of 'contact' means that pack has no list price); CONTACT_FOR_PRICING = pack sizes exist but \
            prices are not shown, tell the customer to contact sales or request a quote for pricing; \
            CONTACT_FOR_QUOTE = no pack sizes are listed, tell the customer to request a quote.""")
    public Object getPackSizesAndPrice(
            @ToolParam(description = "The product's Cat No, e.g. BA072.") String catNo,
            ToolContext toolContext) {
        ChatContext context = ChatContext.from(toolContext);
        return ToolSupport.run(context, "getPackSizesAndPrice", () -> {
            String code = InputValidator.requireCatNo(catNo);
            PricingResult result = pricingService.getPricing(code, context.countryCode());
            if (!result.packSizes().isEmpty()) {
                context.addAction(ChatAction.product(code));
            }
            context.addAction(ChatAction.quote(code));
            return new Pricing(
                    result.catNo(),
                    result.status().name(),
                    result.currency(),
                    result.packSizes().stream().map(p -> new Pack(p.packSize(), p.priceDisplay())).toList(),
                    result.gstNote(),
                    instruction(result));
        });
    }

    private static String instruction(PricingResult result) {
        return switch (result.status()) {
            case PRICED -> "State only these pack sizes and prices, in " + result.currency() + ".";
            case CONTACT_FOR_PRICING -> "Do not state or estimate any price. The customer can contact the sales team"
                    + " or request a quote for pricing.";
            case CONTACT_FOR_QUOTE -> "No pack sizes or prices are listed. The customer can request a quote.";
        };
    }
}
