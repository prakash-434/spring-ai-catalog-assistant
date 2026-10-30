package com.kaarislabs.chatbot.chat;

import java.util.Locale;
import java.util.regex.Pattern;

import jakarta.validation.Valid;

import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/** POST /chat: one non-streaming chat turn. */
@RestController
public class ChatController {

    /** Sets the pricing country; honoured only with the "dev" profile. */
    static final String DEBUG_COUNTRY_HEADER = "X-Debug-Country";

    private static final Pattern COUNTRY = Pattern.compile("^[A-Za-z]{2}$");

    private final ChatService chatService;
    private final ChatContext context;
    private final boolean debugCountryAllowed;

    public ChatController(ChatService chatService, ChatContext context, Environment environment) {
        this.chatService = chatService;
        this.context = context;
        this.debugCountryAllowed = environment.matchesProfiles("dev");
    }

    @PostMapping("/chat")
    public ChatReply chat(@Valid @RequestBody ChatRequest request,
            @RequestHeader(name = DEBUG_COUNTRY_HEADER, required = false) String debugCountry) {
        context.setSessionId(request.sessionId());
        if (debugCountryAllowed && debugCountry != null && COUNTRY.matcher(debugCountry.trim()).matches()) {
            context.setCountryCode(debugCountry.trim().toUpperCase(Locale.ROOT));
        }
        return chatService.chat(request.message().strip());
    }
}
