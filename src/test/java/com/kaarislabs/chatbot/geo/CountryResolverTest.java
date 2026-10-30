package com.kaarislabs.chatbot.geo;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class CountryResolverTest {

    /** Records calls; answers with a fixed result. */
    static final class FakeLookup implements IpCountryLookup {

        final List<String> calls = new ArrayList<>();
        Optional<String> answer = Optional.of("US");

        @Override
        public Optional<String> countryOf(String ip) {
            calls.add(ip);
            return answer;
        }
    }

    final FakeLookup lookup = new FakeLookup();
    final AtomicLong nanos = new AtomicLong();
    final CountryResolver resolver = new CountryResolver(lookup, nanos::get);

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " ", "127.0.0.1", "::1", "10.1.2.3", "172.16.0.9", "192.168.1.20", "169.254.1.1",
            "fe80::1", "fd12:3456::1", "100.64.0.1", "0.0.0.0", "255.255.255.255", "224.0.0.1",
            "8.8.8", "8.8.8.8.8", "256.1.1.1", "08.8.8.8", "8.8.8.8:80", "[2001:4860:4860::8888]",
            "fe80::1%eth0", "localhost", "example.com", "8.8.8.8/../x", "8.8.8.8?x=1", "8.8.8.8 OR 1=1"})
    void unusableAddressesGiveIndiaWithoutALookup(String ip) {
        assertThat(resolver.resolve(ip)).isEqualTo("IN");
        assertThat(lookup.calls).isEmpty();
    }

    @Test
    void publicAddressesAreLookedUp() {
        assertThat(resolver.resolve(" 8.8.8.8 ")).isEqualTo("US");
        assertThat(resolver.resolve("2001:4860:4860::8888")).isEqualTo("US");
        assertThat(lookup.calls).containsExactly("8.8.8.8", "2001:4860:4860:0:0:0:0:8888");
    }

    @Test
    void lookupFailureGivesIndiaAndIsNotCached() {
        lookup.answer = Optional.empty();
        assertThat(resolver.resolve("8.8.8.8")).isEqualTo("IN");

        lookup.answer = Optional.of("US");
        assertThat(resolver.resolve("8.8.8.8")).isEqualTo("US");
        assertThat(lookup.calls).hasSize(2);
    }

    @Test
    void cacheHitAvoidsASecondLookupForAnHour() {
        assertThat(resolver.resolve("8.8.8.8")).isEqualTo("US");
        lookup.answer = Optional.of("DE");

        nanos.addAndGet(Duration.ofMinutes(59).toNanos());
        assertThat(resolver.resolve("8.8.8.8")).isEqualTo("US");
        assertThat(lookup.calls).hasSize(1);

        nanos.addAndGet(Duration.ofMinutes(2).toNanos());
        assertThat(resolver.resolve("8.8.8.8")).isEqualTo("DE");
        assertThat(lookup.calls).hasSize(2);
    }
}
