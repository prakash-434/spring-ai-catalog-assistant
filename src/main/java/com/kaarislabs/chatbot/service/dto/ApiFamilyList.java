package com.kaarislabs.chatbot.service.dto;

import java.util.List;

/** API families, largest first; {@code total} is the number of distinct matching families. */
public record ApiFamilyList(String category, long total, List<ApiFamilyInfo> families) {
}
