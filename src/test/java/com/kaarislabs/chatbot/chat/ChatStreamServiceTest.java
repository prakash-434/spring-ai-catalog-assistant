package com.kaarislabs.chatbot.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.kaarislabs.chatbot.fallback.FallbackResponder;
import com.kaarislabs.chatbot.limits.TokenBudgetTestSupport;
import com.kaarislabs.chatbot.stats.ChatStats;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;

class ChatStreamServiceTest {

    /** Records events; can simulate a client that disconnects after a number of events. */
    static final class ListSink implements ChatEventSink {

        final List<String> names = new ArrayList<>();
        final List<Object> data = new ArrayList<>();
        int failAfter = Integer.MAX_VALUE;
        boolean completed;

        @Override
        public void send(String event, Object payload) throws IOException {
            if (names.size() >= failAfter) {
                throw new IOException("Broken pipe");
            }
            names.add(event);
            data.add(payload);
        }

        @Override
        public void complete() {
            completed = true;
        }
    }

    static final FallbackResponder.Answer FALLBACK_ANSWER =
            new FallbackResponder.Answer("Salbutamol-D9 acetate (Cat No BA044)", List.of(ChatAction.product("BA044")));

    final ChatService chatService = mock(ChatService.class);
    final FallbackResponder fallback = mock(FallbackResponder.class);
    final ChatStats stats = new ChatStats(new BigDecimal("1.50"), new BigDecimal("9.00"));
    final ChatContext context = new ChatContext("s1", "IN");
    OutputGuard guard = (reply, message, ctx) -> Optional.empty();
    long budgetUsed;

    private ChatStreamService service() {
        Clock clock = Clock.fixed(Instant.parse("2026-10-30T06:00:00Z"), ZoneOffset.UTC);
        var budget = TokenBudgetTestSupport.budget(1_000, clock);
        budget.record(budgetUsed);
        when(fallback.answer(anyString(), anyString())).thenReturn(FALLBACK_ANSWER);
        return new ChatStreamService(chatService, guard, e -> false, budget, fallback, stats,
                new SessionEvidence(Duration.ofMinutes(30), 100));
    }

    private static ChatReply llmReply(String text) {
        return new ChatReply(text, List.of(), List.of(), 3, ChatReply.LLM);
    }

    @Test
    void modelReplyIsStreamedAsTokensThenDone() {
        when(chatService.chat(context, "q")).thenReturn(llmReply("raw reply"));
        ListSink sink = new ListSink();

        service().stream(context, "q", sink);

        assertThat(sink.names).containsExactly("token", "done");
        assertThat(sink.data).containsExactly(new ChatStreamService.Token("raw reply"),
                new ChatStreamService.Done(3, ChatReply.LLM));
        assertThat(sink.completed).isTrue();
    }

    @Test
    void guardViolationSendsTheFallbackAndKeepsTheTurnsActions() {
        when(chatService.chat(eq(context), any())).thenAnswer(inv -> {
            context.addAction(ChatAction.quote("BA072"));
            return llmReply("It costs $99.00.");
        });
        guard = (reply, message, ctx) -> Optional.of(new OutputGuard.Violation("unknown_price", "$99.00"));
        ListSink sink = new ListSink();

        service().stream(context, "price of BA044", sink);

        assertThat(sink.data).contains(new ChatStreamService.Token("Salbutamol-D9 acetat"))
                .doesNotContain(new ChatStreamService.Token("It costs $99.00."));
        assertThat(sink.data).containsSubsequence(ChatAction.quote("BA072"), ChatAction.product("BA044"));
        assertThat(sink.data.getLast()).isInstanceOf(ChatStreamService.Done.class)
                .extracting("mode").isEqualTo(ChatReply.FALLBACK);
        assertThat(stats.snapshotAndReset("test")).contains("guardViolations=1", "guard_violation=1");
    }

    @Test
    void exhaustedBudgetNeverCallsTheModel() {
        budgetUsed = 1_000;

        ChatReply reply = service().answer(context, "Do you have BA044?");

        assertThat(reply.mode()).isEqualTo(ChatReply.FALLBACK);
        assertThat(reply.reply()).isEqualTo(FALLBACK_ANSWER.text());
        verify(chatService, never()).chat(any(), anyString());
        assertThat(stats.snapshotAndReset("test")).contains("budget_exceeded=1");
    }

    @Test
    void openCircuitUsesTheFallback() {
        when(chatService.chat(any(), anyString()))
                .thenThrow(CallNotPermittedException.createCallNotPermittedException(CircuitBreaker.ofDefaults("t")));

        ChatReply reply = service().answer(context, "BA044");

        assertThat(reply.mode()).isEqualTo(ChatReply.FALLBACK);
        assertThat(stats.snapshotAndReset("test")).contains("circuit_open=1");
    }

    @Test
    void modelFailureUsesTheFallbackWithItsOwnActionsOnly() {
        when(chatService.chat(eq(context), any())).thenAnswer(inv -> {
            context.addAction(ChatAction.quote("XX1"));
            throw new IllegalStateException("Connection to 10.1.2.3 refused");
        });

        ChatReply reply = service().answer(context, "BA044");

        assertThat(reply.mode()).isEqualTo(ChatReply.FALLBACK);
        assertThat(reply.actions()).containsExactly(ChatAction.product("BA044"));
        assertThat(stats.snapshotAndReset("test")).contains("model_error=1");
    }

    @Test
    void failingFallbackBecomesOneFriendlyErrorEvent() {
        when(chatService.chat(any(), anyString())).thenThrow(new IllegalStateException("model down"));
        ChatStreamService service = service();
        when(fallback.answer(anyString(), anyString()))
                .thenThrow(new IllegalStateException("Cannot connect to 10.1.2.3:1433 as kaaris_chatbot"));
        ListSink sink = new ListSink();

        service.stream(context, "q", sink);

        assertThat(sink.names).containsExactly("error");
        assertThat(sink.data).containsExactly(new ChatStreamService.ErrorMessage(ChatStreamService.FAILED));
        assertThat(sink.completed).isTrue();
    }

    @Test
    void repeatedToolLabelsAreSentOnceInARow() {
        when(chatService.chat(eq(context), any())).thenAnswer(inv -> {
            context.beginToolCall("searchProducts");
            context.beginToolCall("searchProducts");
            context.beginToolCall("listCategories");
            context.beginToolCall("listApiFamilies");
            context.beginToolCall("checkDocuments");
            return llmReply("ok");
        });
        ListSink sink = new ListSink();

        service().stream(context, "q", sink);

        assertThat(sink.data.subList(0, 3)).containsExactly(
                new ChatStreamService.Status("Searching products…"),
                new ChatStreamService.Status(ChatStreamService.DEFAULT_STATUS),
                new ChatStreamService.Status("Checking documents…"));
    }

    @Test
    void clientThatLeavesGetsNoErrorEvent() {
        when(chatService.chat(context, "q")).thenReturn(llmReply("x".repeat(100)));
        ListSink sink = new ListSink();
        sink.failAfter = 2;

        service().stream(context, "q", sink);

        assertThat(sink.names).containsExactly("token", "token");
        assertThat(sink.completed).isTrue();
    }

    @Test
    void chunksAreAbout20CharactersAndKeepSurrogatePairs() {
        assertThat(ChatStreamService.chunks("")).isEmpty();
        assertThat(ChatStreamService.chunks("a".repeat(45))).extracting(String::length).containsExactly(20, 20, 5);

        String emojiAtBoundary = "a".repeat(19) + "😀" + "b";
        List<String> chunks = ChatStreamService.chunks(emojiAtBoundary);
        assertThat(chunks.getFirst()).endsWith("😀").hasSize(21);
        assertThat(String.join("", chunks)).isEqualTo(emojiAtBoundary);
    }
}
