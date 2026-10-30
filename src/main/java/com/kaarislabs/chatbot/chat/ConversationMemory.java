package com.kaarislabs.chatbot.chat;

import java.util.ArrayList;
import java.util.List;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.stereotype.Component;

/** Corrections to a session's chat memory after the model's turn has been stored. */
@Component
public class ConversationMemory {

    private final ChatMemory memory;

    public ConversationMemory(ChatMemory memory) {
        this.memory = memory;
    }

    /**
     * Makes the last turn's answer in memory the text the customer actually saw. Everything after the last user
     * message is the model's reply (Spring AI stores one assistant message per text part) and is replaced by one
     * assistant message; without a user message nothing is changed.
     */
    public void replaceLastReply(String sessionId, String shownText) {
        List<Message> messages = memory.get(sessionId);
        int lastUser = -1;
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (messages.get(i).getMessageType() == MessageType.USER) {
                lastUser = i;
                break;
            }
        }
        if (lastUser < 0) {
            return;
        }
        List<Message> corrected = new ArrayList<>(messages.subList(0, lastUser + 1));
        corrected.add(new AssistantMessage(shownText));
        memory.clear(sessionId);
        memory.add(sessionId, corrected);
    }
}
