package com.kaarislabs.chatbot.chat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.kaarislabs.chatbot.fallback.FallbackResponder;
import com.kaarislabs.chatbot.limits.TokenBudget;
import com.kaarislabs.chatbot.stats.ChatStats;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;

/**
 * One chat turn. {@link #answer} decides between the model and the templated fallback (budget used up, circuit open,
 * model failure, or a reply the {@link OutputGuard} rejects); {@link #stream} writes the result as events: "status"
 * while tools run, the reply as "token" chunks, one "action" per button and "done"; or a single "error". The model
 * call itself is not streamed: the whole reply must exist before the guard can check it.
 */
@Service
public class ChatStreamService {

    private static final Logger log = LoggerFactory.getLogger(ChatStreamService.class);

    static final int CHUNK_SIZE = 20;
    static final String RATE_LIMITED = "I'm getting a lot of questions right now, please try again in a minute.";
    static final String VISITOR_RATE_LIMITED =
            "You're sending messages quickly, please wait a moment and try again.";
    static final String FAILED = "Something went wrong, please try again or contact sales@kaarislabs.com.";
    static final String DEFAULT_STATUS = "Looking that up…";

    /** Why a turn used the fallback; also the keys of the daily stats. */
    public static final String BUDGET = "budget_exceeded";
    public static final String CIRCUIT_OPEN = "circuit_open";
    public static final String MODEL_RATE_LIMITED = "model_rate_limited";
    public static final String MODEL_ERROR = "model_error";
    public static final String GUARD_VIOLATION = "guard_violation";

    private static final Map<String, String> STATUS = Map.of(
            "searchProducts", "Searching products…",
            "getProductDetails", "Reading product details…",
            "getPackSizesAndPrice", "Checking pack sizes…",
            "checkDocuments", "Checking documents…");

    record Status(String label) {
    }

    record Token(String text) {
    }

    /** @param mode {@link ChatReply#LLM} or {@link ChatReply#FALLBACK}, so the widget can show a note */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Done(long latencyMs, String mode) {
    }

    record ErrorMessage(String message) {
    }

    private final ChatService chatService;
    private final OutputGuard outputGuard;
    private final ModelErrorClassifier errors;
    private final TokenBudget budget;
    private final FallbackResponder fallback;
    private final ChatStats stats;
    private final SessionEvidence evidence;
    private final ConversationMemory memory;

    public ChatStreamService(ChatService chatService, OutputGuard outputGuard, ModelErrorClassifier errors,
            TokenBudget budget, FallbackResponder fallback, ChatStats stats, SessionEvidence evidence,
            ConversationMemory memory) {
        this.chatService = chatService;
        this.outputGuard = outputGuard;
        this.errors = errors;
        this.budget = budget;
        this.fallback = fallback;
        this.stats = stats;
        this.evidence = evidence;
        this.memory = memory;
    }

    /**
     * The reply the customer gets: the model's if it was called, succeeded and passed the guard, otherwise the
     * fallback. Throws only if the fallback itself fails (e.g. the database is down).
     */
    public ChatReply answer(ChatContext context, String message) {
        try {
            return decide(context, message);
        } finally {
            // Recorded after the guard has checked this turn, whatever the outcome: tool results are facts from
            // the catalogue even when the reply was rejected or the model failed afterwards.
            evidence.record(context.sessionId(), context.toolResults());
        }
    }

    private ChatReply decide(ChatContext context, String message) {
        long start = System.nanoTime();
        if (budget.exhausted()) {
            return fallback(context, message, BUDGET, false, start);
        }
        ChatReply reply;
        try {
            reply = chatService.chat(context, message);
        } catch (CallNotPermittedException e) {
            return fallback(context, message, CIRCUIT_OPEN, false, start);
        } catch (RuntimeException e) {
            boolean rateLimited = errors.isRateLimited(e);
            Throwable root = NestedExceptionUtils.getMostSpecificCause(e);
            log.warn("Model call failed, using fallback: session={} rateLimited={} {} (cause {}: {})",
                    context.sessionId(), rateLimited, e.getClass().getName(), root.getClass().getName(),
                    root.getMessage());
            return fallback(context, message, rateLimited ? MODEL_RATE_LIMITED : MODEL_ERROR, false, start);
        } finally {
            for (int i = 0; i < context.zeroResultSearches(); i++) {
                stats.zeroResultSearch();
            }
        }
        Optional<OutputGuard.Violation> violation = outputGuard.check(reply.reply(), message, context);
        if (violation.isPresent()) {
            stats.guardViolation();
            log.warn("guard_violation rule={} token=\"{}\" session={}", violation.get().rule(),
                    violation.get().token(), context.sessionId());
            ChatReply shown = fallback(context, message, GUARD_VIOLATION, true, start);
            // The rejected reply is already in the chat memory; the model must not build on it next turn.
            memory.replaceLastReply(context.sessionId(), shown.reply());
            return shown;
        }
        return reply;
    }

    private ChatReply fallback(ChatContext context, String message, String reason, boolean keepTurnActions,
            long start) {
        stats.fallback(reason);
        context.setFallbackReason(reason);
        FallbackResponder.Answer answer = fallback.answer(message, context.countryCode());
        Set<ChatAction> actions = new LinkedHashSet<>();
        if (keepTurnActions) {
            actions.addAll(context.actions());
        }
        actions.addAll(answer.actions());
        log.info("Fallback turn: session={} reason={}", context.sessionId(), reason);
        return new ChatReply(answer.text(), List.copyOf(actions), context.toolsCalled(),
                (System.nanoTime() - start) / 1_000_000, ChatReply.FALLBACK);
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
            sink.send("done", new Done(reply.latencyMs(), reply.mode()));
        } catch (IOException e) {
            log.debug("Client left during chat turn: session={}", context.sessionId());
        } catch (RuntimeException e) {
            Throwable root = NestedExceptionUtils.getMostSpecificCause(e);
            log.error("Chat turn failed: session={} country={} tools={} {}: {} (cause {}: {})",
                    context.sessionId(), context.countryCode(), context.toolsCalled(),
                    e.getClass().getName(), e.getMessage(), root.getClass().getName(), root.getMessage());
            try {
                sink.send("error", new ErrorMessage(FAILED));
            } catch (IOException | RuntimeException ignored) {
                // The client is gone or the stream already timed out; nothing more to tell it.
            }
        } finally {
            sink.complete();
        }
    }

    /** Answers a visitor over the message limit: one "error" event, no model or database call. */
    static void rejectRateLimited(ChatEventSink sink) {
        try {
            sink.send("error", new ErrorMessage(VISITOR_RATE_LIMITED));
        } catch (IOException | RuntimeException ignored) {
            // The client is gone.
        }
        sink.complete();
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
