package com.kaarislabs.chatbot.chat;

import org.springframework.stereotype.Component;

/** Placeholder until the real checks exist: returns the reply unchanged. */
@Component
public class PassThroughOutputGuard implements OutputGuard {

    @Override
    public String apply(String reply, ChatContext context) {
        return reply;
    }
}
