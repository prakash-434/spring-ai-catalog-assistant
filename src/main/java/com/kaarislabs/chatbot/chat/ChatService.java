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

/** One chat turn: the model answers with the tools (non-streaming), memory is kept per session id. */
@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    static final String FALLBACK_REPLY = "Sorry, I could not answer that. Please rephrase your question, or contact"
            + " our sales team at sales@kaarislabs.com.";

    private final ChatClient chatClient;

    public ChatService(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    /**
     * Runs the whole tool-calling turn. The tools read and fill {@code context}.
     *
     * @param message a validated, stripped user message
     */
    public ChatReply chat(ChatContext context, String message) {
        long start = System.nanoTime();
        String reply;
        Usage usage = null;
        try {
            ChatResponse response = chatClient.prompt()
                    .user(message)
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, context.sessionId()))
                    .toolContext(Map.of(ChatContext.TOOL_CONTEXT_KEY, context))
                    .call()
                    .chatResponse();
            reply = text(response);
            usage = response == null ? null : response.getMetadata().getUsage();
        } catch (ToolCallLimitExceededException e) {
            log.warn("Tool call backstop hit: session={} tool={} limit={}", context.sessionId(), e.getToolName(),
                    e.getLimit());
            reply = FALLBACK_REPLY;
        }
        long latencyMs = (System.nanoTime() - start) / 1_000_000;

        log.info("Chat turn: session={} country={} tools={} latencyMs={} promptTokens={} completionTokens={}"
                        + " totalTokens={}",
                context.sessionId(), context.countryCode(), context.toolsCalled(), latencyMs,
                usage == null ? null : usage.getPromptTokens(),
                usage == null ? null : usage.getCompletionTokens(),
                usage == null ? null : usage.getTotalTokens());
        return new ChatReply(reply, context.actions(), context.toolsCalled(), latencyMs);
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
