package com.kaarislabs.chatbot.chat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.stereotype.Service;

/**
 * Streams one chat turn as events: "status" while tools run, then the guarded reply as "token" chunks, one "action"
 * per button and "done"; or a single "error". The model call itself is not streamed: the whole reply must exist
 * before {@link OutputGuard} can check it.
 */
@Service
public class ChatStreamService {

    private static final Logger log = LoggerFactory.getLogger(ChatStreamService.class);

    static final int CHUNK_SIZE = 20;
    static final String RATE_LIMITED = "I'm getting a lot of questions right now, please try again in a minute.";
    static final String FAILED = "Something went wrong, please try again or contact sales@kaarislabs.com.";
    static final String DEFAULT_STATUS = "Looking that up…";

    private static final Map<String, String> STATUS = Map.of(
            "searchProducts", "Searching products…",
            "getProductDetails", "Reading product details…",
            "getPackSizesAndPrice", "Checking pack sizes…",
            "checkDocuments", "Checking documents…");

    record Status(String label) {
    }

    record Token(String text) {
    }

    record Done(long latencyMs) {
    }

    record ErrorMessage(String message) {
    }

    private final ChatService chatService;
    private final OutputGuard outputGuard;
    private final ModelErrorClassifier errors;

    public ChatStreamService(ChatService chatService, OutputGuard outputGuard, ModelErrorClassifier errors) {
        this.chatService = chatService;
        this.outputGuard = outputGuard;
        this.errors = errors;
    }

    /** The guarded, non-streamed reply. */
    public ChatReply answer(ChatContext context, String message) {
        ChatReply reply = chatService.chat(context, message);
        return new ChatReply(outputGuard.apply(reply.reply(), context), reply.actions(), reply.toolsCalled(),
                reply.latencyMs());
    }

    /** Runs the turn and writes its events; always completes the sink. */
    public void stream(ChatContext context, String message, ChatEventSink sink) {
        try {
            context.onToolStart(new StatusSender(sink));
            ChatReply reply = answer(context, message);
            for (String chunk : chunks(reply.reply())) {
                sink.send("token", new Token(chunk));
            }
            for (ChatAction action : reply.actions()) {
                sink.send("action", action);
            }
            sink.send("done", new Done(reply.latencyMs()));
        } catch (IOException e) {
            log.debug("Client left during chat turn: session={}", context.sessionId());
        } catch (RuntimeException e) {
            boolean rateLimited = errors.isRateLimited(e);
            Throwable root = NestedExceptionUtils.getMostSpecificCause(e);
            log.error("Chat turn failed: session={} country={} tools={} rateLimited={} {}: {} (cause {}: {})",
                    context.sessionId(), context.countryCode(), context.toolsCalled(), rateLimited,
                    e.getClass().getName(), e.getMessage(), root.getClass().getName(), root.getMessage());
            try {
                sink.send("error", new ErrorMessage(rateLimited ? RATE_LIMITED : FAILED));
            } catch (IOException | RuntimeException ignored) {
                // The client is gone or the stream already timed out; nothing more to tell it.
            }
        } finally {
            sink.complete();
        }
    }

    static String statusLabel(String tool) {
        return STATUS.getOrDefault(tool, DEFAULT_STATUS);
    }

    /** About {@value #CHUNK_SIZE} characters each, never splitting a surrogate pair. */
    static List<String> chunks(String text) {
        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + CHUNK_SIZE, text.length());
            if (end < text.length() && Character.isHighSurrogate(text.charAt(end - 1))) {
                end++;
            }
            chunks.add(text.substring(start, end));
            start = end;
        }
        return chunks;
    }

    /**
     * Sends a status line per tool, skipping a repeat of the same label. Failures are ignored: a lost status line
     * must not abort the tool, and a gone client is noticed on the next token.
     */
    private static final class StatusSender implements Consumer<String> {

        private final ChatEventSink sink;
        private String last;

        StatusSender(ChatEventSink sink) {
            this.sink = sink;
        }

        @Override
        public void accept(String tool) {
            String label = statusLabel(tool);
            if (label.equals(last)) {
                return;
            }
            last = label;
            try {
                sink.send("status", new Status(label));
            } catch (IOException | RuntimeException e) {
                log.debug("Status not sent: {}", e.getClass().getSimpleName());
            }
        }
    }
}
