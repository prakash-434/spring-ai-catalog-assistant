package com.kaarislabs.chatbot.chat;

import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.model.tool.ToolCallLimitExceededException;
import org.springframework.stereotype.Service;

import com.kaarislabs.chatbot.limits.TokenBudget;
import com.kaarislabs.chatbot.stats.ChatStats;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;

/** One model turn: the model answers with the tools (non-streaming), memory is kept per session id. */
@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    static final String FALLBACK_REPLY = "Sorry, I could not answer that. Please rephrase your question, or contact"
            + " our sales team at sales@kaarislabs.com.";

    private final ChatClient chatClient;
    private final CircuitBreaker circuitBreaker;
    private final TokenBudget budget;
    private final ChatStats stats;

    public ChatService(ChatClient chatClient, CircuitBreaker modelCircuitBreaker, TokenBudget budget,
            ChatStats stats) {
        this.chatClient = chatClient;
        this.circuitBreaker = modelCircuitBreaker;
        this.budget = budget;
        this.stats = stats;
    }

    /**
     * Runs the whole tool-calling turn. The tools read and fill {@code context}.
     *
     * @param message a validated, cleaned user message
     * @throws CallNotPermittedException while the circuit breaker is open (the model is not called)
     * @throws RuntimeException          when the model call fails
     */
    public ChatReply chat(ChatContext context, String message) {
        long start = System.nanoTime();
        String reply;
        Usage usage = null;
        try {
            ChatResponse response = circuitBreaker.executeSupplier(() -> chatClient.prompt()
                    .user(message)
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, context.sessionId()))
                    .toolContext(Map.of(ChatContext.TOOL_CONTEXT_KEY, context))
                    .call()
                    .chatResponse());
            reply = text(response);
            usage = response == null ? null : response.getMetadata().getUsage();
        } catch (ToolCallLimitExceededException e) {
            log.warn("Tool call backstop hit: session={} tool={} limit={}", context.sessionId(), e.getToolName(),
                    e.getLimit());
            reply = FALLBACK_REPLY;
        }
        long latencyMs = (System.nanoTime() - start) / 1_000_000;

        long input = usage == null || usage.getPromptTokens() == null ? 0 : usage.getPromptTokens();
        long output = outputTokens(usage, input);
        budget.record(input + output);
        stats.llmTurn(input, output);

        log.info("Chat turn: session={} country={} tools={} latencyMs={} inputTokens={} outputTokens={}"
                        + " estimatedCostUsd={}",
                context.sessionId(), context.countryCode(), context.toolsCalled(), latencyMs, input, output,
                stats.estimatedCost(input, output));
        return new ChatReply(reply, context.actions(), context.toolsCalled(), latencyMs, ChatReply.LLM);
    }

    /** Output includes thinking tokens: the total minus the input when the model reports a total. */
    private static long outputTokens(Usage usage, long input) {
        if (usage == null) {
            return 0;
        }
        if (usage.getTotalTokens() != null && usage.getTotalTokens() >= input) {
            return usage.getTotalTokens() - input;
        }
        return usage.getCompletionTokens() == null ? 0 : usage.getCompletionTokens();
    }

    /**
     * The whole reply. Gemini can split one answer into several text parts and Spring AI maps each part to its own
     * generation, so {@code getResult()} alone would return only the first part.
     */
    static String text(ChatResponse response) {
        if (response == null) {
            return FALLBACK_REPLY;
        }
        String text = response.getResults().stream()
                .map(g -> g.getOutput().getText())
                .filter(Objects::nonNull)
                .collect(Collectors.joining());
        return text.isBlank() ? FALLBACK_REPLY : text.strip();
    }
}
