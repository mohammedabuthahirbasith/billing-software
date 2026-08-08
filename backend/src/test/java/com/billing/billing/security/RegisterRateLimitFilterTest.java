package com.billing.billing.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RegisterRateLimitFilterTest {

    private static final int MAX_ATTEMPTS_PER_WINDOW = 5;

    private final RegisterRateLimitFilter filter = new RegisterRateLimitFilter();

    private MockHttpServletResponse register(String remoteAddr, String forwardedFor) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/register");
        request.setRemoteAddr(remoteAddr);
        if (forwardedFor != null) {
            request.addHeader("X-Forwarded-For", forwardedFor);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }

    @Test
    void allowsUpToTheWindowLimitThenReturns429() throws Exception {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS_PER_WINDOW; attempt++) {
            assertThat(register("10.0.0.1", null).getStatus()).as("attempt %d", attempt).isEqualTo(200);
        }

        MockHttpServletResponse blocked = register("10.0.0.1", null);

        assertThat(blocked.getStatus()).isEqualTo(429);
        assertThat(blocked.getErrorMessage()).contains("Too many registration attempts");
    }

    @Test
    void limitsPerClientIpIndependently() throws Exception {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS_PER_WINDOW + 1; attempt++) {
            register("10.0.0.1", null);
        }

        assertThat(register("10.0.0.2", null).getStatus()).isEqualTo(200);
    }

    @Test
    void usesTheFirstForwardedForEntryAsTheClientIpBehindAProxy() throws Exception {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS_PER_WINDOW + 1; attempt++) {
            register("10.0.0.9", "203.0.113.5, 10.0.0.9");
        }

        // Same proxy socket address, different real client → its own fresh window.
        assertThat(register("10.0.0.9", "203.0.113.6, 10.0.0.9").getStatus()).isEqualTo(200);
        assertThat(register("10.0.0.9", "203.0.113.5, 10.0.0.9").getStatus()).isEqualTo(429);
    }

    @Test
    void ignoresBlankForwardedForAndFallsBackToTheSocketAddress() throws Exception {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS_PER_WINDOW + 1; attempt++) {
            register("10.0.0.3", "   ");
        }

        assertThat(register("10.0.0.3", null).getStatus()).isEqualTo(429);
    }

    @Test
    void doesNotLimitOtherPathsOrMethods() throws Exception {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS_PER_WINDOW + 5; attempt++) {
            MockHttpServletRequest login = new MockHttpServletRequest("POST", "/api/auth/login");
            login.setRemoteAddr("10.0.0.4");
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(login, response, new MockFilterChain());
            assertThat(response.getStatus()).isEqualTo(200);

            MockHttpServletRequest get = new MockHttpServletRequest("GET", "/api/auth/register");
            get.setRemoteAddr("10.0.0.4");
            MockHttpServletResponse getResponse = new MockHttpServletResponse();
            filter.doFilter(get, getResponse, new MockFilterChain());
            assertThat(getResponse.getStatus()).isEqualTo(200);
        }
    }
}
