package com.kaarislabs.chatbot.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UrlPathHelper;

/**
 * Only the website's server may call the chat API: every request must carry X-Internal-Token equal to
 * CHATBOT_INTERNAL_TOKEN, except health checks and, in the "dev" profile, /dev/** and /chat/json. The rule is an
 * allowlist, so path variants such as "/chat;x" cannot slip past it. Without a configured token every protected
 * request is rejected.
 */
@Component
public class InternalTokenFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(InternalTokenFilter.class);

    public static final String HEADER = "X-Internal-Token";

    private static final String VERIFIED = InternalTokenFilter.class.getName() + ".verified";
    private static final UrlPathHelper PATHS = new UrlPathHelper();

    private final byte[] expectedDigest;
    private final boolean dev;

    public InternalTokenFilter(@Value("${kaaris.chat.internal-token:}") String token, Environment environment) {
        this.expectedDigest = token == null || token.isBlank() ? null : sha256(token);
        this.dev = environment.matchesProfiles("dev");
        if (expectedDigest == null) {
            log.warn("CHATBOT_INTERNAL_TOKEN is not set: every chat request will be rejected with 401");
        }
    }

    /** True when the request carried the correct token, so headers set by the website can be trusted. */
    public static boolean isVerified(HttpServletRequest request) {
        return Boolean.TRUE.equals(request.getAttribute(VERIFIED));
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = PATHS.getPathWithinApplication(request);
        if (path.equals("/actuator/health") || path.startsWith("/actuator/health/")) {
            return true;
        }
        return dev && (path.startsWith("/dev/") || path.equals("/chat/json"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!matches(request.getHeader(HEADER))) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write("{\"error\":\"Unauthorized\"}");
            return;
        }
        request.setAttribute(VERIFIED, Boolean.TRUE);
        chain.doFilter(request, response);
    }

    /** Compares digests with {@link MessageDigest#isEqual}, which takes the same time wherever the values differ. */
    private boolean matches(String token) {
        if (expectedDigest == null || token == null || token.isEmpty()) {
            return false;
        }
        return MessageDigest.isEqual(expectedDigest, sha256(token));
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
