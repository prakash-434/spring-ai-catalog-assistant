package com.kaarislabs.chatbot.tools;

import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;

import com.kaarislabs.chatbot.chat.ChatContext;
import com.kaarislabs.chatbot.common.InvalidInputException;

/** Shared tool plumbing: per-turn call limit, and short error results for the model instead of exceptions. */
final class ToolSupport {

    private static final Logger log = LoggerFactory.getLogger(ToolSupport.class);

    static final ToolError LIMIT_REACHED = new ToolError(
            "Tool call limit for this question reached. Answer now using only the tool results you already have.");
    static final ToolError UNAVAILABLE = new ToolError(
            "The product catalogue is temporarily unavailable. Tell the customer to try again shortly or contact the"
                    + " sales team.");

    private ToolSupport() {
    }

    static Object run(ChatContext context, String tool, Supplier<Object> body) {
        if (!context.beginToolCall(tool)) {
            return LIMIT_REACHED;
        }
        try {
            Object result = body.get();
            context.recordToolResult(result);
            return result;
        } catch (InvalidInputException e) {
            return new ToolError(e.getMessage());
        } catch (DataAccessException e) {
            log.warn("Tool {} failed: {}", tool, e.getClass().getSimpleName());
            return UNAVAILABLE;
        }
    }

    /** Returned to the model instead of a result. */
    public record ToolError(String error) {
    }
}
