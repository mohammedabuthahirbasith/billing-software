package com.billing.billing.security;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

// The two permitAll POST endpoints both deserve a per-IP cap, for different reasons: /register
// provisions a whole new Store per hit (a bigger blast radius than a typical signup), and /login is
// an unthrottled password oracle otherwise — passwords are only length-validated, so an
// unrestricted login endpoint is a workable online brute-force target. A simple in-memory per-IP
// fixed-window limiter is the right level of engineering for a single-instance, no-Redis
// deployment. Known simplification: the map of per-IP windows grows with every distinct IP ever
// seen and is never swept — acceptable at this project's real traffic volume, and a free-tier host
// that sleeps/restarts periodically resets it anyway; a real cleanup task would be the next step if
// traffic ever justified it.
//
// The window key comes from getRemoteAddr() only. X-Forwarded-For is NOT read here: any client can
// send that header itself, so parsing it directly means an attacker rotates one header value per
// request and gets an unlimited number of fresh windows — i.e. no limit at all. Behind a proxy that
// strips and re-sets the header (Render), set server.forward-headers-strategy=framework so Spring's
// own ForwardedHeaderFilter resolves the real client into getRemoteAddr() before this filter runs;
// that keeps "is the proxy trustworthy" a deployment decision instead of an assumption baked in here.
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private static final Map<String, Integer> MAX_ATTEMPTS_BY_PATH = Map.of(
            "/api/auth/register", 5,
            "/api/auth/login", 20);
    private static final long WINDOW_MILLIS = 60 * 60 * 1000; // 1 hour

    private final ConcurrentHashMap<String, Window> windowsByKey = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        Integer maxAttempts = "POST".equalsIgnoreCase(request.getMethod())
                ? MAX_ATTEMPTS_BY_PATH.get(request.getRequestURI())
                : null;
        if (maxAttempts == null) {
            filterChain.doFilter(request, response);
            return;
        }

        // Keyed per path as well as per IP so a burst of logins can't consume the register budget.
        String key = request.getRequestURI() + "|" + request.getRemoteAddr();
        long now = System.currentTimeMillis();
        Window window = windowsByKey.computeIfAbsent(key, k -> new Window(now));

        synchronized (window) {
            if (now - window.windowStartMillis > WINDOW_MILLIS) {
                window.windowStartMillis = now;
                window.count.set(0);
            }
            if (window.count.incrementAndGet() > maxAttempts) {
                // 429 Too Many Requests — not one of HttpServletResponse's named SC_* constants.
                response.sendError(429, "Too many attempts, please try again later");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private static final class Window {
        volatile long windowStartMillis;
        final AtomicInteger count = new AtomicInteger(0);

        Window(long now) {
            this.windowStartMillis = now;
        }
    }
}
