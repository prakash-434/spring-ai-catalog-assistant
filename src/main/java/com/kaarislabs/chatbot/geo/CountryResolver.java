package com.kaarislabs.chatbot.geo;

import java.time.Duration;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;

/**
 * Visitor country for pricing. Anything that is not a successful lookup of a public IP gives "IN", the same default
 * the website uses. Successful lookups are cached; failures are not, so they are retried on the next turn.
 */
@Component
public class CountryResolver {

    public static final String FALLBACK_COUNTRY = "IN";

    static final Duration CACHE_TTL = Duration.ofHours(1);
    static final long CACHE_SIZE = 50_000;

    private final IpCountryLookup lookup;
    private final Cache<String, String> cache;

    @Autowired
    public CountryResolver(IpCountryLookup lookup) {
        this(lookup, Ticker.systemTicker());
    }

    CountryResolver(IpCountryLookup lookup, Ticker ticker) {
        this.lookup = lookup;
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(CACHE_TTL)
                .maximumSize(CACHE_SIZE)
                .ticker(ticker)
                .build();
    }

    /** @param clientIp the visitor's IP, or null when unknown or not trusted */
    public String resolve(String clientIp) {
        Optional<String> ip = IpAddresses.publicAddress(clientIp);
        if (ip.isEmpty()) {
            return FALLBACK_COUNTRY;
        }
        String cached = cache.getIfPresent(ip.get());
        if (cached != null) {
            return cached;
        }
        Optional<String> country = lookup.countryOf(ip.get());
        country.ifPresent(c -> cache.put(ip.get(), c));
        return country.orElse(FALLBACK_COUNTRY);
    }
}
