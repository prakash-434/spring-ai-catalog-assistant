package com.kaarislabs.chatbot.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import com.kaarislabs.chatbot.chat.ChatContext;
import com.kaarislabs.chatbot.chat.ChatService;
import com.kaarislabs.chatbot.limits.TokenBudgetTestSupport;
import com.kaarislabs.chatbot.stats.ChatStats;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;

/** The production circuit breaker configuration around a failing model. */
class ModelCircuitBreakerTest {

    @Test
    void opensAfterFiveFailuresAndThenSkipsTheModel() {
        ChatClient chatClient = mock(ChatClient.class);
        when(chatClient.prompt()).thenThrow(new IllegalStateException("model unavailable"));
        CircuitBreaker breaker = new AiConfig().modelCircuitBreaker();
        ChatService service = new ChatService(chatClient, breaker,
                TokenBudgetTestSupport.budget(1_000_000,
                        Clock.fixed(Instant.parse("2026-10-30T06:00:00Z"), ZoneOffset.UTC)),
                new ChatStats(BigDecimal.ONE, BigDecimal.ONE));
        ChatContext context = new ChatContext("s1", "IN");

        for (int i = 0; i < 4; i++) {
            assertThatThrownBy(() -> service.chat(context, "q")).isInstanceOf(IllegalStateException.class);
            assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        }
        assertThatThrownBy(() -> service.chat(context, "q")).isInstanceOf(IllegalStateException.class);
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        assertThatThrownBy(() -> service.chat(context, "q")).isInstanceOf(CallNotPermittedException.class);
        verify(chatClient, times(5)).prompt();
    }
}
