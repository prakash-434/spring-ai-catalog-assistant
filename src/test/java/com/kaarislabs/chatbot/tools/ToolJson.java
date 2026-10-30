package com.kaarislabs.chatbot.tools;

import static org.assertj.core.api.Assertions.assertThat;

import org.springframework.ai.tool.execution.DefaultToolCallResultConverter;

/** Serialises a tool result exactly as Spring AI hands it to the model. */
final class ToolJson {

    private static final DefaultToolCallResultConverter CONVERTER = new DefaultToolCallResultConverter();

    private ToolJson() {
    }

    static String json(Object result) {
        return CONVERTER.convert(result, result.getClass());
    }

    /** Links belong in actions and brand is not on the product page, so neither may reach the model. */
    static String assertNoLinksOrBrand(Object result) {
        String json = json(result);
        assertThat(json).doesNotContainIgnoringCase("url")
                .doesNotContain("/product/")
                .doesNotContain("http")
                .doesNotContainIgnoringCase("brand")
                .doesNotContain("Witega");
        return json;
    }
}
