package com.kaarislabs.chatbot.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Body of POST /chat.
 *
 * @param sessionId chosen by the website per visitor conversation; keys the chat memory
 */
public record ChatRequest(
        @NotBlank(message = "sessionId is required.")
        @Size(max = 64, message = "sessionId can be at most 64 characters.")
        @Pattern(regexp = "^[A-Za-z0-9_-]*$", message = "sessionId may contain only letters, digits, '-' and '_'.")
        String sessionId,

        @NotBlank(message = "Please enter a message.")
        @Size(max = ChatRequest.MAX_MESSAGE_LENGTH, message = "A message can be at most 500 characters.")
        String message) {

    public static final int MAX_MESSAGE_LENGTH = 500;
}
