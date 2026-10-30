package com.kaarislabs.chatbot.chat;

import java.io.IOException;
import java.util.concurrent.Executor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.kaarislabs.chatbot.limits.MessageRateLimiter;
import com.kaarislabs.chatbot.stats.ChatStats;

/** POST /chat: one chat turn as a text/event-stream (see {@link ChatStreamService} for the events). */
@RestController
public class ChatController {

    static final long STREAM_TIMEOUT_MS = 60_000;

    private final ChatStreamService streams;
    private final ChatContextFactory contexts;
    private final MessageRateLimiter rateLimiter;
    private final ChatStats stats;
    private final Executor chatExecutor;

    public ChatController(ChatStreamService streams, ChatContextFactory contexts, MessageRateLimiter rateLimiter,
            ChatStats stats, @Qualifier("chatExecutor") Executor chatExecutor) {
        this.streams = streams;
        this.contexts = contexts;
        this.rateLimiter = rateLimiter;
        this.stats = stats;
        this.chatExecutor = chatExecutor;
    }

    @PostMapping("/chat")
    public ResponseEntity<SseEmitter> chat(@Valid @RequestBody ChatRequest request, HttpServletRequest http) {
        stats.message();
        ChatContextFactory.Origin origin = contexts.origin(http);
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);

        if (!rateLimiter.tryAcquire(origin.visitorKey())) {
            stats.rateLimited();
            ChatStreamService.rejectRateLimited(new SseEventSink(emitter));
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(emitter);
        }

        emitter.onTimeout(() -> sendTimeout(emitter));
        chatExecutor.execute(() -> streams.stream(contexts.create(request.sessionId(), origin), request.message(),
                new SseEventSink(emitter)));
        return ResponseEntity.ok(emitter);
    }

    private static void sendTimeout(SseEmitter emitter) {
        try {
            emitter.send(SseEmitter.event().name("error")
                    .data(new ChatStreamService.ErrorMessage(ChatStreamService.FAILED), MediaType.APPLICATION_JSON));
        } catch (IOException | RuntimeException ignored) {
            // The client is already gone.
        }
        emitter.complete();
    }
}
