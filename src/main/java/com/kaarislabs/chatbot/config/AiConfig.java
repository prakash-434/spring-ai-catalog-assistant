package com.kaarislabs.chatbot.config;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import com.kaarislabs.chatbot.chat.CaffeineChatMemoryRepository;
import com.kaarislabs.chatbot.tools.CompanyTools;
import com.kaarislabs.chatbot.tools.DocumentTools;
import com.kaarislabs.chatbot.tools.PricingTools;
import com.kaarislabs.chatbot.tools.ProductTools;

/**
 * Chat client wiring. The model provider is chosen by the starter in pom.xml and its spring.ai.* properties; nothing
 * here depends on it, so another provider's starter can replace it.
 */
@Configuration
public class AiConfig {

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
