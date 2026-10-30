package com.kaarislabs.chatbot.chat;

import java.io.IOException;
import java.util.concurrent.Executor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** POST /chat: one chat turn as a text/event-stream (see {@link ChatStreamService} for the events). */
@RestController
public class ChatController {

    static final long STREAM_TIMEOUT_MS = 60_000;

    private final ChatStreamService streams;
    private final ChatContextFactory contexts;
    private final Executor chatExecutor;

    public ChatController(ChatStreamService streams, ChatContextFactory contexts,
            @Qualifier("chatExecutor") Executor chatExecutor) {
        this.streams = streams;
        this.contexts = contexts;
        this.chatExecutor = chatExecutor;
    }

    @PostMapping("/chat")
    public SseEmitter chat(@Valid @RequestBody ChatRequest request, HttpServletRequest http) {
        ChatContextFactory.Origin origin = contexts.origin(http);
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);
        emitter.onTimeout(() -> sendTimeout(emitter));
        chatExecutor.execute(() -> streams.stream(contexts.create(request.sessionId(), origin),
                request.message().strip(), new SseEventSink(emitter)));
        return emitter;
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
