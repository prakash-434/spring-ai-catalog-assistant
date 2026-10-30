package com.kaarislabs.chatbot.chat;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Errors raised before a turn starts: 400 for bad requests. For /chat/json also 429 when the model is rate limited
 * and 503 otherwise; /chat reports turn failures as an "error" event instead. Bodies are always JSON, even when the
 * client asked for text/event-stream.
 */
@RestControllerAdvice(assignableTypes = {ChatController.class, ChatJsonController.class})
public class ChatExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ChatExceptionHandler.class);

    private final ModelErrorClassifier errors;

    public ChatExceptionHandler(ModelErrorClassifier errors) {
        this.errors = errors;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> invalid(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .findFirst()
                .orElse("Invalid request.");
        return error(HttpStatus.BAD_REQUEST, message);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> unreadable(HttpMessageNotReadableException e) {
        return error(HttpStatus.BAD_REQUEST, "The request body must be JSON with sessionId and message.");
    }

    /** Model or network failures. Only types and messages are logged; neither contains the API key. */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, String>> modelFailure(RuntimeException e) {
        if (errors.isRateLimited(e)) {
            log.warn("Model rate limited: {}", e.getClass().getSimpleName());
            return error(HttpStatus.TOO_MANY_REQUESTS, ChatStreamService.RATE_LIMITED);
        }
        Throwable root = NestedExceptionUtils.getMostSpecificCause(e);
        log.error("Chat turn failed: {}: {} (cause {}: {})", e.getClass().getName(), e.getMessage(),
                root.getClass().getName(), root.getMessage());
        return error(HttpStatus.SERVICE_UNAVAILABLE, ChatStreamService.FAILED);
    }

    private static ResponseEntity<Map<String, String>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(Map.of("error", message));
    }
}
