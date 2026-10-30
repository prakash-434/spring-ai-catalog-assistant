package com.kaarislabs.chatbot.geo;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/** ip-api.com lookup, the same service the website uses. */
@Component
public class IpApiCountryLookup implements IpCountryLookup {

    private static final Logger log = LoggerFactory.getLogger(IpApiCountryLookup.class);
    private static final Pattern COUNTRY = Pattern.compile("^[A-Z]{2}$");

    record IpApiResponse(String status, String countryCode) {
    }

    private final HttpClient http;
    private final JsonMapper json;
    private final String baseUrl;
    private final Duration timeout;

    public IpApiCountryLookup(JsonMapper json,
            @Value("${kaaris.geo.lookup-url}") String baseUrl,
            @Value("${kaaris.geo.lookup-timeout}") Duration timeout) {
        this.json = json;
        this.baseUrl = baseUrl;
        this.timeout = timeout;
        this.http = HttpClient.newBuilder().connectTimeout(timeout).build();
    }

    @Override
    public Optional<String> countryOf(String ip) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + ip + "?fields=status,countryCode"))
                .timeout(timeout)
                .GET()
                .build();
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("Country lookup returned HTTP {}", response.statusCode());
                return Optional.empty();
            }
            IpApiResponse body = json.readValue(response.body(), IpApiResponse.class);
            if (!"success".equals(body.status()) || body.countryCode() == null
                    || !COUNTRY.matcher(body.countryCode()).matches()) {
                return Optional.empty();
            }
            return Optional.of(body.countryCode());
        } catch (IOException | JacksonException e) {
            log.warn("Country lookup failed: {}", e.getClass().getSimpleName());
            return Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }
}
