package com.kaarislabs.chatbot.common;

/** Thrown when user-supplied input fails validation; the message is safe to show to the user. */
public class InvalidInputException extends RuntimeException {

    public InvalidInputException(String message) {
        super(message);
    }
}
