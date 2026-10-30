package com.kaarislabs.chatbot.chat;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

/**
 * State of one /chat request, shared by the controller, the chat service and the tools. Tools run on the request
 * thread during the (non-streaming) model call, so the request scope reaches them.
 */
@Component
@RequestScope
public class ChatContext {

    public static final String DEFAULT_COUNTRY = "IN";
    public static final int MAX_TOOL_CALLS = 5;

    private String sessionId;
    private String countryCode = DEFAULT_COUNTRY;
    private final Set<ChatAction> actions = new LinkedHashSet<>();
    private final List<String> toolsCalled = new ArrayList<>();

    public String sessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    /** ISO-3166 alpha-2 code used for pricing; "IN" unless set. */
    public String countryCode() {
        return countryCode;
    }

    public void setCountryCode(String countryCode) {
        this.countryCode = countryCode;
    }

    /**
     * Records a tool call.
     *
     * @return false when this call is over the per-turn limit and must not run
     */
    public boolean beginToolCall(String tool) {
        toolsCalled.add(tool);
        return toolsCalled.size() <= MAX_TOOL_CALLS;
    }

    /** Adds a button; duplicates are kept once, in first-seen order. */
    public void addAction(ChatAction action) {
        actions.add(action);
    }

    public List<ChatAction> actions() {
        return List.copyOf(actions);
    }

    public List<String> toolsCalled() {
        return List.copyOf(toolsCalled);
    }
}
