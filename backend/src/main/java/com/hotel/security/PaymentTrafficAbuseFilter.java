package com.hotel.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Bounds public provider callbacks and authenticated payment-status polling at the HTTP boundary.
 */
public final class PaymentTrafficAbuseFilter extends OncePerRequestFilter {

    private static final long WINDOW_MILLIS = 60_000L;
    private static final String PROPERTY_PROVIDER_PREFIX = "/api/payment-providers/property/";
    private static final String PLATFORM_PROVIDER_PREFIX = "/api/payment-providers/platform/";

    private final int callbackMaxBodyBytes;
    private final FixedWindowRateLimiter callbackLimiter;
    private final FixedWindowRateLimiter pollingLimiter;

    public PaymentTrafficAbuseFilter(
            int callbackMaxBodyBytes,
            int callbackRequestsPerMinute,
            int pollingRequestsPerMinute,
            int maximumTrackedClients) {
        if (callbackMaxBodyBytes < 1) {
            throw new IllegalArgumentException("callbackMaxBodyBytes must be positive.");
        }
        this.callbackMaxBodyBytes = callbackMaxBodyBytes;
        this.callbackLimiter = new FixedWindowRateLimiter(
                callbackRequestsPerMinute, maximumTrackedClients, Clock.systemUTC());
        this.pollingLimiter = new FixedWindowRateLimiter(
                pollingRequestsPerMinute, maximumTrackedClients, Clock.systemUTC());
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        if (isProviderCallback(request)) {
            handleProviderCallback(request, response, filterChain);
            return;
        }

        if (isPaymentAttemptPolling(request)) {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (isAuthenticated(authentication)) {
                RateLimitDecision decision = pollingLimiter.acquire(principalKey(authentication));
                if (!decision.allowed()) {
                    rejectRateLimit(response, decision.retryAfterSeconds());
                    return;
                }
            }
        }

        filterChain.doFilter(request, response);
    }

    private void handleProviderCallback(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        if (request.getContentLengthLong() > callbackMaxBodyBytes) {
            rejectPayloadTooLarge(response);
            return;
        }

        RateLimitDecision decision = callbackLimiter.acquire(sourceAddress(request));
        if (!decision.allowed()) {
            rejectRateLimit(response, decision.retryAfterSeconds());
            return;
        }

        byte[] body = request.getInputStream().readNBytes(callbackMaxBodyBytes + 1);
        if (body.length > callbackMaxBodyBytes) {
            rejectPayloadTooLarge(response);
            return;
        }
        filterChain.doFilter(new BufferedBodyRequest(request, body), response);
    }

    private boolean isProviderCallback(HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return false;
        }
        String path = request.getRequestURI();
        if (isCurrentProviderCallback(path)) {
            return true;
        }
        return path.equals("/api/payments/vnpay-callback")
                || path.equals("/api/payments/vnpay-ipn")
                || path.equals("/api/payments/momo-ipn")
                || path.equals("/api/payments/zalopay-callback");
    }

    private boolean isCurrentProviderCallback(String path) {
        String suffix;
        if (path.startsWith(PROPERTY_PROVIDER_PREFIX)) {
            suffix = path.substring(PROPERTY_PROVIDER_PREFIX.length());
        } else if (path.startsWith(PLATFORM_PROVIDER_PREFIX)) {
            suffix = path.substring(PLATFORM_PROVIDER_PREFIX.length());
        } else {
            return false;
        }
        int separator = suffix.indexOf('/');
        if (separator <= 0 || separator == suffix.length() - 1) {
            return false;
        }
        String action = suffix.substring(separator + 1);
        return action.equals("callback") || action.equals("refund-callback");
    }

    private boolean isPaymentAttemptPolling(HttpServletRequest request) {
        if (!"GET".equalsIgnoreCase(request.getMethod())) {
            return false;
        }
        String path = request.getRequestURI();
        if (!path.startsWith("/api/payment-attempts/")) {
            return false;
        }
        return path.indexOf('/', "/api/payment-attempts/".length()) < 0;
    }

    private boolean isAuthenticated(Authentication authentication) {
        return authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
    }

    private String principalKey(Authentication authentication) {
        if (authentication.getPrincipal() instanceof CustomUserDetails details) {
            return "user:" + details.getUserId();
        }
        return "principal:" + authentication.getName();
    }

    private String sourceAddress(HttpServletRequest request) {
        String remoteAddress = request.getRemoteAddr();
        return remoteAddress == null || remoteAddress.isBlank() ? "unknown" : remoteAddress;
    }

    private void rejectRateLimit(HttpServletResponse response, long retryAfterSeconds) throws IOException {
        response.setHeader("Retry-After", Long.toString(retryAfterSeconds));
        writeError(response, HttpStatus.TOO_MANY_REQUESTS.value(),
                "RATE_LIMITED", "Too many payment requests. Retry later.");
    }

    private void rejectPayloadTooLarge(HttpServletResponse response) throws IOException {
        writeError(response, HttpStatus.PAYLOAD_TOO_LARGE.value(),
                "PAYLOAD_TOO_LARGE", "Provider callback payload exceeds the allowed size.");
    }

    private void writeError(HttpServletResponse response, int status, String code, String message) throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader("Cache-Control", "no-store");
        response.getWriter().write("{\"status\":" + status + ",\"code\":\"" + code
                + "\",\"message\":\"" + message + "\"}");
    }

    private record RateLimitDecision(boolean allowed, long retryAfterSeconds) {
    }

    private static final class FixedWindowRateLimiter {

        private final int maximumRequests;
        private final int maximumTrackedClients;
        private final Clock clock;
        private final Map<String, WindowCounter> counters = new ConcurrentHashMap<>();
        private final AtomicLong lastCleanupWindow = new AtomicLong(-1L);

        private FixedWindowRateLimiter(int maximumRequests, int maximumTrackedClients, Clock clock) {
            if (maximumRequests < 1 || maximumTrackedClients < 1) {
                throw new IllegalArgumentException("Rate-limit bounds must be positive.");
            }
            this.maximumRequests = maximumRequests;
            this.maximumTrackedClients = maximumTrackedClients;
            this.clock = clock;
        }

        private RateLimitDecision acquire(String clientKey) {
            long now = clock.millis();
            long window = now / WINDOW_MILLIS;
            cleanupExpiredWindows(window);

            WindowCounter current = counters.get(clientKey);
            if (current == null && counters.size() >= maximumTrackedClients) {
                return new RateLimitDecision(false, retryAfterSeconds(now));
            }

            AtomicLong requestCount = new AtomicLong();
            counters.compute(clientKey, (key, existing) -> {
                if (existing == null || existing.window() != window) {
                    requestCount.set(1L);
                    return new WindowCounter(window, 1L);
                }
                long nextCount = existing.requests() + 1L;
                requestCount.set(nextCount);
                return new WindowCounter(window, nextCount);
            });
            return new RateLimitDecision(
                    requestCount.get() <= maximumRequests,
                    retryAfterSeconds(now));
        }

        private void cleanupExpiredWindows(long currentWindow) {
            long previousCleanup = lastCleanupWindow.get();
            if (previousCleanup == currentWindow
                    || !lastCleanupWindow.compareAndSet(previousCleanup, currentWindow)) {
                return;
            }
            counters.entrySet().removeIf(entry -> entry.getValue().window() < currentWindow);
        }

        private long retryAfterSeconds(long now) {
            long remainingMillis = WINDOW_MILLIS - (now % WINDOW_MILLIS);
            return Math.max(1L, (remainingMillis + 999L) / 1_000L);
        }
    }

    private record WindowCounter(long window, long requests) {
    }

    private static final class BufferedBodyRequest extends HttpServletRequestWrapper {

        private final byte[] body;

        private BufferedBodyRequest(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream input = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override
                public boolean isFinished() {
                    return input.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener readListener) {
                    // Synchronous MVC requests do not use non-blocking callbacks.
                }

                @Override
                public int read() {
                    return input.read();
                }

                @Override
                public int read(byte[] bytes, int offset, int length) {
                    return input.read(bytes, offset, length);
                }
            };
        }

        @Override
        public int getContentLength() {
            return body.length;
        }

        @Override
        public long getContentLengthLong() {
            return body.length;
        }
    }
}
