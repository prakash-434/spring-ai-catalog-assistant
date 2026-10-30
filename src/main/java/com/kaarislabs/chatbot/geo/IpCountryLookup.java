package com.kaarislabs.chatbot.geo;

import java.util.Optional;

/** Country of a public IP address. */
public interface IpCountryLookup {

    /**
     * @param ip a validated, public IPv4/IPv6 literal
     * @return the ISO-3166 alpha-2 code, or empty when unknown or the lookup failed; never throws
     */
    Optional<String> countryOf(String ip);
}
