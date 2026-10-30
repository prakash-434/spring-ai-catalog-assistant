package com.kaarislabs.chatbot.chat;

import java.io.IOException;

import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** Writes chat events to an SSE response; each event's data is one JSON object, so it never spans lines. */
final class SseEventSink implements ChatEventSink {

    private final SseEmitter emitter;

    SseEventSink(SseEmitter emitter) {
        this.emitter = emitter;
    }

    @Override
    public void send(String event, Object data) throws IOException {
        emitter.send(SseEmitter.event().name(event).data(data, MediaType.APPLICATION_JSON));
    }

    @Override
    public void complete() {
        emitter.complete();
    }
}
