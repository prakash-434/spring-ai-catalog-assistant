package com.kaarislabs.chatbot.repository.row;

/** A category or API family name with its number of website-visible products. */
public record NameCount(String name, long productCount) {
}
