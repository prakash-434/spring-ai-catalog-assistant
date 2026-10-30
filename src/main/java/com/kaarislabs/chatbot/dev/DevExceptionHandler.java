package com.kaarislabs.chatbot.dev;

import java.util.Map;

import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.kaarislabs.chatbot.common.InvalidInputException;

/** Turns validation failures on the dev endpoints into 400 responses with the user-facing message. */
@Profile("dev")
@RestControllerAdvice(assignableTypes = DevController.class)
public class DevExceptionHandler {

    @ExceptionHandler(InvalidInputException.class)
    public ResponseEntity<Map<String, String>> invalidInput(InvalidInputException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
    }
}
