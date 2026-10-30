package com.kaarislabs.chatbot.geo;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpServer;

import tools.jackson.databind.json.JsonMapper;

/** Against a local stand-in for ip-api.com. */
class IpApiCountryLookupTest {

    HttpServer server;
    final AtomicReference<String> requestedUri = new AtomicReference<>();
    volatile String body = "{\"status\":\"success\",\"countryCode\":\"US\"}";
    volatile int status = 200;
    volatile long delayMs;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/json/", exchange -> {
            requestedUri.set(exchange.getRequestURI().toString());
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private IpApiCountryLookup lookup(Duration timeout) {
        String base = "http://127.0.0.1:" + server.getAddress().getPort() + "/json/";
        return new IpApiCountryLookup(JsonMapper.builder().build(), base, timeout);
    }

    @Test
    void successReturnsTheCountryAndAsksOnlyForTwoFields() {
        assertThat(lookup(Duration.ofSeconds(2)).countryOf("8.8.8.8")).contains("US");
        assertThat(requestedUri.get()).isEqualTo("/json/8.8.8.8?fields=status,countryCode");
    }

    @Test
    void failedStatusIsEmpty() {
        body = "{\"status\":\"fail\"}";
        assertThat(lookup(Duration.ofSeconds(2)).countryOf("8.8.8.8")).isEmpty();
    }

    @Test
    void unexpectedCountryValueIsEmpty() {
        body = "{\"status\":\"success\",\"countryCode\":\"<script>\"}";
        assertThat(lookup(Duration.ofSeconds(2)).countryOf("8.8.8.8")).isEmpty();
    }

    @Test
    void httpErrorOrBadJsonIsEmpty() {
        status = 429;
        assertThat(lookup(Duration.ofSeconds(2)).countryOf("8.8.8.8")).isEmpty();
        status = 200;
        body = "not json";
        assertThat(lookup(Duration.ofSeconds(2)).countryOf("8.8.8.8")).isEmpty();
    }

    @Test
    void slowAnswerTimesOutAsEmpty() {
        delayMs = 1_500;
        long start = System.nanoTime();

        assertThat(lookup(Duration.ofMillis(300)).countryOf("8.8.8.8")).isEmpty();
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofMillis(1_200));
    }
}
