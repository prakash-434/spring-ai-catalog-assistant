package com.kaarislabs.chatbot.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class InternalTokenFilterTest {

    static final String TOKEN = "s3cret-token";

    private static InternalTokenFilter filter(String token, String... profiles) {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles(profiles);
        return new InternalTokenFilter(token, env);
    }

    private record Outcome(int status, boolean passed, boolean verified) {
    }

    private static Outcome call(InternalTokenFilter filter, String path, String token) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        if (token != null) {
            request.addHeader(InternalTokenFilter.HEADER, token);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        return new Outcome(response.getStatus(), chain.getRequest() != null, InternalTokenFilter.isVerified(request));
    }

    @Test
    void missingOrWrongTokenIs401() throws Exception {
        InternalTokenFilter filter = filter(TOKEN);

        assertThat(call(filter, "/chat", null)).isEqualTo(new Outcome(401, false, false));
        assertThat(call(filter, "/chat", "")).isEqualTo(new Outcome(401, false, false));
        assertThat(call(filter, "/chat", "s3cret-tokeN")).isEqualTo(new Outcome(401, false, false));
        assertThat(call(filter, "/chat", TOKEN + "x")).isEqualTo(new Outcome(401, false, false));
    }

    @Test
    void correctTokenPassesAndMarksTheRequestVerified() throws Exception {
        assertThat(call(filter(TOKEN), "/chat", TOKEN)).isEqualTo(new Outcome(200, true, true));
    }

    @Test
    void pathVariantsAndUnknownPathsAlsoNeedTheToken() throws Exception {
        InternalTokenFilter filter = filter(TOKEN);

        assertThat(call(filter, "/chat;x=1", null).status()).isEqualTo(401);
        assertThat(call(filter, "/chat/", null).status()).isEqualTo(401);
        assertThat(call(filter, "/chat/json", null).status()).isEqualTo(401);
        assertThat(call(filter, "/dev/search", null).status()).isEqualTo(401);
    }

    @Test
    void healthIsAlwaysOpen() throws Exception {
        assertThat(call(filter(TOKEN), "/actuator/health", null)).isEqualTo(new Outcome(200, true, false));
    }

    @Test
    void devRoutesAndJsonChatAreExemptOnlyInDev() throws Exception {
        InternalTokenFilter dev = filter(TOKEN, "dev");

        assertThat(call(dev, "/dev/search", null).passed()).isTrue();
        assertThat(call(dev, "/chat/json", null).passed()).isTrue();
        assertThat(call(dev, "/chat/json", null).verified()).isFalse();
        assertThat(call(dev, "/chat", null).status()).isEqualTo(401);
    }

    @Test
    void withoutAConfiguredTokenEverythingIsRejected() throws Exception {
        InternalTokenFilter filter = filter("  ");

        assertThat(call(filter, "/chat", "").status()).isEqualTo(401);
        assertThat(call(filter, "/chat", "  ").status()).isEqualTo(401);
    }
}
