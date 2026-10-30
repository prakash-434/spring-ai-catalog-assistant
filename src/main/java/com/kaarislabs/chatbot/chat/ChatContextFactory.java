package com.kaarislabs.chatbot.chat;

import java.util.Locale;
import java.util.regex.Pattern;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import com.kaarislabs.chatbot.geo.CountryResolver;
import com.kaarislabs.chatbot.geo.IpAddresses;
import com.kaarislabs.chatbot.security.InternalTokenFilter;

/**
 * Builds the {@link ChatContext} of a turn. Headers are read on the request thread ({@link #origin}); the country
 * lookup, which may take up to the lookup timeout, runs later on the chat thread ({@link #create}).
 */
@Component
public class ChatContextFactory {

    /** The visitor's IP, set by the website's server; trusted only with a valid internal token. */
    public static final String CLIENT_IP_HEADER = "X-Client-IP";
    /** Pricing country override; honoured only with the "dev" profile. */
    public static final String DEBUG_COUNTRY_HEADER = "X-Debug-Country";

    private static final Pattern COUNTRY = Pattern.compile("^[A-Za-z]{2}$");

    /**
     * What a request says about the visitor.
     *
     * @param trustedClientIp X-Client-IP from a token-validated request, else null
     * @param debugCountry    the dev-only country override, else null
     * @param visitorKey      who the rate limits apply to: the trusted client IP if it is a valid IP literal,
     *                        otherwise the connection's remote address
     */
    public record Origin(String trustedClientIp, String debugCountry, String visitorKey) {
    }

    private final CountryResolver countries;
    private final boolean dev;

    public ChatContextFactory(CountryResolver countries, Environment environment) {
        this.countries = countries;
        this.dev = environment.matchesProfiles("dev");
    }

    public Origin origin(HttpServletRequest request) {
        String ip = InternalTokenFilter.isVerified(request) ? request.getHeader(CLIENT_IP_HEADER) : null;
        String debug = dev ? request.getHeader(DEBUG_COUNTRY_HEADER) : null;
        if (debug != null && COUNTRY.matcher(debug.trim()).matches()) {
            debug = debug.trim().toUpperCase(Locale.ROOT);
        } else {
            debug = null;
        }
        String visitorKey = IpAddresses.normalized(ip).orElse(request.getRemoteAddr());
        return new Origin(ip, debug, visitorKey);
    }

    public ChatContext create(String sessionId, Origin origin) {
        String country = origin.debugCountry() != null
                ? origin.debugCountry()
                : countries.resolve(origin.trustedClientIp());
        return new ChatContext(sessionId, country);
    }
}
