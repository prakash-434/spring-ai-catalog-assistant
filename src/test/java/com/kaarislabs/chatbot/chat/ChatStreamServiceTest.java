package com.kaarislabs.chatbot.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

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

    final ChatService chatService = mock(ChatService.class);
    final ChatContext context = new ChatContext("s1", "IN");

    @Test
    void guardSeesTheWholeReplyBeforeAnyTokenIsSent() {
        when(chatService.chat(context, "q")).thenReturn(new ChatReply("raw reply", List.of(), List.of(), 3));
        ChatStreamService service = new ChatStreamService(chatService, (reply, ctx) -> reply.toUpperCase(), e -> false);
        ListSink sink = new ListSink();

        service.stream(context, "q", sink);

        assertThat(sink.names).containsExactly("token", "done");
        assertThat(sink.data.getFirst()).isEqualTo(new ChatStreamService.Token("RAW REPLY"));
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
            return new ChatReply("ok", List.of(), context.toolsCalled(), 1);
        });
        ListSink sink = new ListSink();

        new ChatStreamService(chatService, (r, c) -> r, e -> false).stream(context, "q", sink);

        assertThat(sink.data.subList(0, 3)).containsExactly(
                new ChatStreamService.Status("Searching products…"),
                new ChatStreamService.Status(ChatStreamService.DEFAULT_STATUS),
                new ChatStreamService.Status("Checking documents…"));
    }

    @Test
    void clientThatLeavesGetsNoErrorEvent() {
        when(chatService.chat(context, "q")).thenReturn(new ChatReply("x".repeat(100), List.of(), List.of(), 1));
        ListSink sink = new ListSink();
        sink.failAfter = 2;

        new ChatStreamService(chatService, (r, c) -> r, e -> false).stream(context, "q", sink);

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
