package com.kaarislabs.chatbot.tools;

import java.util.Locale;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import com.kaarislabs.chatbot.chat.ChatAction;
import com.kaarislabs.chatbot.chat.ChatContext;
import com.kaarislabs.chatbot.config.CompanyProperties;
import com.kaarislabs.chatbot.tools.ToolResults.CompanyInfo;
import com.kaarislabs.chatbot.tools.ToolSupport.ToolError;

/** Contact details and website pages from company-info.yml. */
@Component
public class CompanyTools {

    static final String TOPICS = "contact, address, quote, complaint, documents, stock_list";

    private final CompanyProperties company;
    private final ChatContext context;

    public CompanyTools(CompanyProperties company, ChatContext context) {
        this.company = company;
        this.context = context;
    }

    @Tool(name = "getCompanyInfo", description = """
            Kaaris Labs contact details and where on the website to do something. Topics: contact (phone and \
            email), address (postal address), quote (how to request a quote), complaint (how to raise a complaint), \
            documents (where to download COAs and other batch documents), stock_list (the current stock list).""")
    public Object getCompanyInfo(
            @ToolParam(description = "One of: " + TOPICS + ".") String topic) {
        return ToolSupport.run(context, "getCompanyInfo", () -> {
            String t = topic == null ? "" : topic.trim().toLowerCase(Locale.ROOT);
            CompanyProperties.Links links = company.links();
            return switch (t) {
                case "contact" -> {
                    page(links.quote());
                    yield info(t, null, company.phone(), company.email(), null);
                }
                case "address" -> info(t, company.address(), company.phone(), company.email(), null);
                case "quote" -> {
                    page(links.quote());
                    yield info(t, null, company.phone(), company.email(),
                            "Customers request a quote with the contact form; mention the Cat No and pack size.");
                }
                case "complaint" -> {
                    page(links.complaints());
                    yield info(t, null, company.phone(), company.email(),
                            "Complaints are raised with the complaint form.");
                }
                case "documents" -> {
                    page(links.documents());
                    yield info(t, null, null, company.email(),
                            "COAs and other batch documents are downloaded on the documents page with the item code"
                                    + " and the batch number from the vial label.");
                }
                case "stock_list" -> {
                    page(links.stockList());
                    yield info(t, null, null, company.email(), "The current stock list is on the stock list page.");
                }
                default -> new ToolError("Unknown topic. Use one of: " + TOPICS + ".");
            };
        });
    }

    private CompanyInfo info(String topic, String address, String phone, String email, String note) {
        return new CompanyInfo(topic, company.name(), address, phone, email, note);
    }

    private void page(String path) {
        if (path != null) {
            context.addAction(ChatAction.page(path));
        }
    }
}
