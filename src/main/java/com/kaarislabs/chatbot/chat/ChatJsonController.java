package com.kaarislabs.chatbot.chat;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** POST /chat/json: the same turn as one JSON response, for tests and debugging. "dev" profile only. */
@Profile("dev")
@RestController
public class ChatJsonController {

    private final ChatStreamService streams;
    private final ChatContextFactory contexts;

    public ChatJsonController(ChatStreamService streams, ChatContextFactory contexts) {
        this.streams = streams;
        this.contexts = contexts;
    }

    @PostMapping("/chat/json")
    public ChatReply chat(@Valid @RequestBody ChatRequest request, HttpServletRequest http) {
        ChatContext context = contexts.create(request.sessionId(), contexts.origin(http));
        return streams.answer(context, request.message().strip());
    }
}
