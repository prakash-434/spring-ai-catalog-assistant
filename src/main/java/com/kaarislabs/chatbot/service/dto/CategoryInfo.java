package com.kaarislabs.chatbot.service.dto;

/** A website category with its number of visible products; {@code url} is relative to the website. */
public record CategoryInfo(String name, long productCount, String url) {
}
