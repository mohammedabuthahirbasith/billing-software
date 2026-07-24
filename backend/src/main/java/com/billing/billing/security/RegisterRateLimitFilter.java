package com.billing.billing.security;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

// POST /api/auth/register is permitAll and, since multi-tenancy, provisions a whole new Store per
// hit, not just a user row — a bigger blast radius than a typical signup endpoint. A simple
// in-memory per-IP fixed-window limiter is the right level of engineering for a single-instance,
// no-Redis deployment: it stops a scripted flood of fake stores without adding infrastructure this
// project doesn't otherwise need. Known simplification: the map of per-IP windows grows with every
// distinct IP ever seen and is never swept — acceptable at this project's real traffic volume, and
// a free-tier host that sleeps/restarts periodically resets it anyway; a real cleanup task would be
// the next step if traffic ever justified it.
public class RegisterRateLimitFilter extends OncePerRequestFilter {

    private static final String LIMITED_PATH = "/api/auth/register";
    private static final int MAX_ATTEMPTS_PER_WINDOW = 5;
    private static final long WINDOW_MILLIS = 60 * 60 * 1000; // 1 hour

    private final ConcurrentHashMap<String, Window> windowsByIp = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        if (!"POST".equalsIgnoreCase(request.getMethod()) || !LIMITED_PATH.equals(request.getRequestURI())) {
            filterChain.doFilter(request, response);
            return;
        }

        String ip = clientIp(request);
        long now = System.currentTimeMillis();
        Window window = windowsByIp.computeIfAbsent(ip, k -> new Window(now));

        synchronized (window) {
            if (now - window.windowStartMillis > WINDOW_MILLIS) {
                window.windowStartMillis = now;
                window.count.set(0);
            }
            if (window.count.incrementAndGet() > MAX_ATTEMPTS_PER_WINDOW) {
                // 429 Too Many Requests — not one of HttpServletResponse's named SC_* constants.
                response.sendError(429, "Too many registration attempts, please try again later");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    // Render sits behind a proxy, so the direct socket address is the load balancer's, not the
    // client's — X-Forwarded-For carries the real one when present.
    private String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private static final class Window {
        volatile long windowStartMillis;
        final AtomicInteger count = new AtomicInteger(0);

        Window(long now) {
            this.windowStartMillis = now;
        }
    }
}
