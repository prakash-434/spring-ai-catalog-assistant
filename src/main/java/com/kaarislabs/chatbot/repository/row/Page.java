package com.kaarislabs.chatbot.repository.row;

import java.util.List;

/** A capped list of rows plus the total number that matched. */
public record Page<T>(long total, List<T> items) {

    public Page {
        items = List.copyOf(items);
    }
}
