package com.kaarislabs.chatbot.tools;

import static com.kaarislabs.chatbot.tools.ToolJson.assertNoLinksOrBrand;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.ai.chat.model.ToolContext;

import com.kaarislabs.chatbot.chat.ChatAction;
import com.kaarislabs.chatbot.chat.ChatContext;
import com.kaarislabs.chatbot.config.CompanyProperties;
import com.kaarislabs.chatbot.tools.ToolResults.CompanyInfo;
import com.kaarislabs.chatbot.tools.ToolSupport.ToolError;

class CompanyToolsTest {

    static final CompanyProperties COMPANY = new CompanyProperties("Kaaris Research Labs Private Limited",
            "https://kaarislabs.com", "Plot No. 177, Hyderabad", "+91 95155 17891", "sales@kaarislabs.com",
            new CompanyProperties.Links("/contact", "/documents", "/complaints", "/stock-list"));

    ChatContext context;
    ToolContext toolContext;
    CompanyTools tools;

    @BeforeEach
    void setUp() {
        context = new ChatContext("s1", null);
        toolContext = context.toToolContext();
        tools = new CompanyTools(COMPANY);
    }

    @ParameterizedTest
    @CsvSource({
            "contact, /contact",
            "quote, /contact",
            "complaint, /complaints",
            "documents, /documents",
            "stock_list, /stock-list",
            "' Stock_List ', /stock-list"})
    void topicsRecordTheirPageAction(String topic, String path) {
        Object result = tools.getCompanyInfo(topic, toolContext);

        assertThat(result).isInstanceOf(CompanyInfo.class);
        assertNoLinksOrBrand(result);
        assertThat(context.actions()).containsExactly(ChatAction.page(path));
    }

    @Test
    void contactGivesPhoneAndEmail() {
        CompanyInfo info = (CompanyInfo) tools.getCompanyInfo("contact", toolContext);

        assertThat(info.phone()).isEqualTo("+91 95155 17891");
        assertThat(info.email()).isEqualTo("sales@kaarislabs.com");
    }

    @Test
    void addressHasNoButton() {
        CompanyInfo info = (CompanyInfo) tools.getCompanyInfo("address", toolContext);

        assertThat(info.address()).isEqualTo("Plot No. 177, Hyderabad");
        assertThat(context.actions()).isEmpty();
    }

    @Test
    void unknownTopicReturnsTheValidTopics() {
        Object result = tools.getCompanyInfo("careers", toolContext);

        assertThat(result).isInstanceOf(ToolError.class);
        assertThat(((ToolError) result).error()).contains(CompanyTools.TOPICS);
        assertThat(context.actions()).isEmpty();
    }
}
