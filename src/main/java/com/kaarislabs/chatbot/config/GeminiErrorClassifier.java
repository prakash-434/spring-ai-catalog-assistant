package com.kaarislabs.chatbot.config;

import org.springframework.stereotype.Component;

import com.google.genai.errors.ApiException;
import com.kaarislabs.chatbot.chat.ModelErrorClassifier;

/**
 * Gemini SDK errors are not wrapped by Spring AI; HTTP 429 means rate limit or quota. The only Gemini-specific code
 * besides {@link GeminiConfig}.
 */
@Component
public class GeminiErrorClassifier implements ModelErrorClassifier {

    private static final int TOO_MANY_REQUESTS = 429;

    @Override
    public boolean isRateLimited(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (t instanceof ApiException api && api.code() == TOO_MANY_REQUESTS) {
                return true;
            }
        }
        return false;
    }
}
