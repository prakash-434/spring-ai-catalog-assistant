package com.kaarislabs.chatbot.chat;

/**
 * Last check on a reply before the customer sees it. Runs on the complete reply text, after all tools have run, so it
 * can compare the reply with what the tools returned (via {@link ChatContext}).
 */
@FunctionalInterface
public interface OutputGuard {

    /** @return the reply to send, possibly changed */
    String apply(String reply, ChatContext context);
}
