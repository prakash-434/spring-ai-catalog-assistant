package com.kaarislabs.chatbot.config;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.google.genai.Client;
import com.google.genai.types.HttpOptions;
import com.google.genai.types.HttpRetryOptions;

/**
 * Replaces the starter's Gemini client to add a request timeout: without one a slow or stuck model call holds the
 * request for minutes. The SDK's own retries are off so that a failure is not retried twice (Spring AI retries too).
 * The only Gemini-specific code besides {@link GeminiErrorClassifier}.
 */
@Configuration
public class GeminiConfig {

    @Bean
    Client googleGenAiClient(@Value("${spring.ai.google.genai.api-key}") String apiKey,
            @Value("${kaaris.chat.model-timeout:30s}") Duration timeout) {
        return Client.builder()
                .apiKey(apiKey)
                .httpOptions(HttpOptions.builder()
                        .timeout(Math.toIntExact(timeout.toMillis()))
                        .retryOptions(HttpRetryOptions.builder().attempts(1).build())
                        .build())
                .build();
    }
}
