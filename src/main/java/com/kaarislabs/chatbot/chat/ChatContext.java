package com.kaarislabs.chatbot.chat;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

import org.springframework.ai.chat.model.ToolContext;

/**
 * State of one chat turn, shared by the chat service and the tools. The turn runs on its own (virtual) thread, so the
 * context travels to the tools in Spring AI's {@link ToolContext} rather than in a request scope. Tools of one turn
 * run one after another, so no synchronisation is needed.
 */
public class ChatContext {

    public static final String DEFAULT_COUNTRY = "IN";
    public static final int MAX_TOOL_CALLS = 5;

    /** Key of this object in the tool context map. */
    static final String TOOL_CONTEXT_KEY = "kaaris.chatContext";

    private final String sessionId;
    private final String countryCode;
    private final Set<ChatAction> actions = new LinkedHashSet<>();
    private final List<String> toolsCalled = new ArrayList<>();
    private final List<Object> toolResults = new ArrayList<>();
    private int zeroResultSearches;
    private long inputTokens;
    private long outputTokens;
    private String fallbackReason;
    private Consumer<String> toolListener = tool -> { };

    /** @param countryCode ISO-3166 alpha-2 code used for pricing; null means {@value #DEFAULT_COUNTRY} */
    public ChatContext(String sessionId, String countryCode) {
        this.sessionId = sessionId;
        this.countryCode = countryCode == null ? DEFAULT_COUNTRY : countryCode;
    }

    /** The context the chat service put into the tool context of this turn. */
    public static ChatContext from(ToolContext toolContext) {
        Object context = toolContext == null ? null : toolContext.getContext().get(TOOL_CONTEXT_KEY);
        return Objects.requireNonNull((ChatContext) context, "No ChatContext in the tool context");
    }

    /** For tests and the chat service: a tool context carrying this chat context. */
    public ToolContext toToolContext() {
        return new ToolContext(Map.of(TOOL_CONTEXT_KEY, this));
    }

    public String sessionId() {
        return sessionId;
    }

    public String countryCode() {
        return countryCode;
    }

    /** Called with the tool name each time a tool starts, e.g. to stream a status line. */
    public void onToolStart(Consumer<String> listener) {
        this.toolListener = Objects.requireNonNull(listener);
    }

    /**
     * Records a tool call.
     *
     * @return false when this call is over the per-turn limit and must not run
     */
    public boolean beginToolCall(String tool) {
        toolsCalled.add(tool);
        boolean allowed = toolsCalled.size() <= MAX_TOOL_CALLS;
        if (allowed) {
            toolListener.accept(tool);
        }
        return allowed;
    }

    /** Model tokens used by this turn, as reported by the model. */
    public void recordTokens(long input, long output) {
        inputTokens += input;
        outputTokens += output;
    }

    public long inputTokens() {
        return inputTokens;
    }

    public long outputTokens() {
        return outputTokens;
    }

    /** Why this turn used the templated fallback; null when the model answered. */
    public String fallbackReason() {
        return fallbackReason;
    }

    void setFallbackReason(String reason) {
        this.fallbackReason = reason;
    }

    public void zeroResultSearch() {
        zeroResultSearches++;
    }

    public int zeroResultSearches() {
        return zeroResultSearches;
    }

    /** Keeps what a tool returned to the model, for the output guard. */
    public void recordToolResult(Object result) {
        toolResults.add(result);
    }

    /** Everything the tools returned to the model in this turn, in call order. */
    public List<Object> toolResults() {
        return List.copyOf(toolResults);
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
