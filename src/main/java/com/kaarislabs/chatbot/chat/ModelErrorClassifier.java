package com.kaarislabs.chatbot.chat;

/** Recognises provider-specific model errors, so the chat code needs no provider classes. */
public interface ModelErrorClassifier {

    /** True when the model provider rejected the call because of a rate limit or quota. */
    boolean isRateLimited(Throwable error);
}
