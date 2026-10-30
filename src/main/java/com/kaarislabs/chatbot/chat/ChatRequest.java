package com.kaarislabs.chatbot.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Body of POST /chat.
 *
 * @param sessionId a UUID chosen by the website per visitor conversation; keys the chat memory
 */
public record ChatRequest(
        @NotBlank(message = "sessionId is required.")
        @Pattern(regexp = ChatRequest.UUID, message = "sessionId must be a UUID.")
        String sessionId,

        @NotBlank(message = "Please enter a message.")
        @Size(max = ChatRequest.MAX_MESSAGE_LENGTH, message = "A message can be at most 500 characters.")
        String message) {

    public static final int MAX_MESSAGE_LENGTH = 500;
    static final String UUID = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$";
}
