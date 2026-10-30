package com.kaarislabs.chatbot.chat;

import java.io.IOException;

/** Where a streamed chat turn is written; an SSE response in production, a list in tests. */
public interface ChatEventSink {

    /**
     * @param data serialised as JSON
     * @throws IOException when the client is gone
     */
    void send(String event, Object data) throws IOException;

    void complete();
}
