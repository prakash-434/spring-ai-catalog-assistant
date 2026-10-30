package com.kaarislabs.chatbot.chat;

import java.util.List;

/** Response of POST /chat. */
public record ChatReply(String reply, List<ChatAction> actions, List<String> toolsCalled, long latencyMs) {
}
