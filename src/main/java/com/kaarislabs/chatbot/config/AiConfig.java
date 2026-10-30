package com.kaarislabs.chatbot.config;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.model.tool.ToolCallLimitExceededException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import com.kaarislabs.chatbot.chat.CaffeineChatMemoryRepository;
import com.kaarislabs.chatbot.tools.CompanyTools;
import com.kaarislabs.chatbot.tools.DocumentTools;
import com.kaarislabs.chatbot.tools.PricingTools;
import com.kaarislabs.chatbot.tools.ProductTools;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;

/**
 * Chat client wiring. The model provider is chosen by the starter in pom.xml and its spring.ai.* properties; nothing
 * here depends on it, so another provider's starter can replace it.
 */
@Configuration
public class AiConfig {

    private static final Logger log = LoggerFactory.getLogger(AiConfig.class);

    /** Messages kept per session (user and assistant turns). */
    static final int MEMORY_WINDOW = 10;

    @Bean
    ChatMemory chatMemory(@Value("${kaaris.chat.session-idle-timeout}") Duration idleTimeout,
            @Value("${kaaris.chat.max-sessions}") long maxSessions) {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(new CaffeineChatMemoryRepository(idleTimeout, maxSessions))
                .maxMessages(MEMORY_WINDOW)
                .build();
    }

    /**
     * Stops calling the model after repeated failures. Resilience4j opens on a failure rate, so "5 failures within
     * 60 seconds" is approximated as: at least 5 calls in the last 60 seconds, half of them failed. A run of pure
     * failures therefore opens on the 5th. Open for 60 seconds, then one trial call (half-open).
     */
    @Bean
    CircuitBreaker modelCircuitBreaker() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindow(60, 5, CircuitBreakerConfig.SlidingWindowType.TIME_BASED)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(60))
                .permittedNumberOfCallsInHalfOpenState(1)
                .ignoreExceptions(ToolCallLimitExceededException.class)
                .build();
        CircuitBreaker breaker = CircuitBreaker.of("model", config);
        breaker.getEventPublisher().onStateTransition(e -> log.warn("model_circuit_breaker {} -> {}",
                e.getStateTransition().getFromState(), e.getStateTransition().getToState()));
        return breaker;
    }

    /** Runs chat turns, which block on the model and the database for seconds, on virtual threads. */
    @Bean(destroyMethod = "close")
    ExecutorService chatExecutor() {
        return Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("chat-", 0).factory());
    }

    @Bean
    ChatClient chatClient(ChatClient.Builder builder, ChatMemory chatMemory,
            @Value("classpath:prompts/system.txt") Resource systemPrompt,
            ProductTools productTools, PricingTools pricingTools, DocumentTools documentTools,
            CompanyTools companyTools) {
        return builder
                .defaultSystem(read(systemPrompt))
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .defaultTools(productTools, pricingTools, documentTools, companyTools)
                .build();
    }

    /** Read once at startup so a missing or unreadable prompt stops the application. */
    private static String read(Resource resource) {
        try {
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read system prompt " + resource, e);
        }
    }
}
