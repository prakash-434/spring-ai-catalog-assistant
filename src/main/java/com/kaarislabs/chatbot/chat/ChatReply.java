package com.kaarislabs.chatbot.chat;

import java.util.List;

/**
 * Response of a chat turn.
 *
 * @param mode {@value #LLM} when the model answered, {@value #FALLBACK} for the templated answer without the model
 */
public record ChatReply(String reply, List<ChatAction> actions, List<String> toolsCalled, long latencyMs,
        String mode) {

    public static final String LLM = "llm";
    public static final String FALLBACK = "fallback";
}
