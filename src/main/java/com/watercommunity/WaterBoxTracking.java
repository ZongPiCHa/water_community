package com.watercommunity;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Builds backend events that follow the water-box protocol and sends them after the business transaction commits.
 */
@Component
class WaterBoxTracker {
    static final String THEME_COMMUNITY = "community";

    private static final Logger log = LoggerFactory.getLogger(WaterBoxTracker.class);
    private static final int MAX_STRING_LENGTH = 128;

    private final ObjectProvider<WaterBoxSender> sender;
    private final String env;

    WaterBoxTracker(ObjectProvider<WaterBoxSender> sender, @Value("${water-box.env:test}") String env) {
        this.sender = sender;
        this.env = "prod".equals(env) ? "prod" : "test";
    }

    void track(String action, CommunityUser user, Map<String, Object> properties) {
        track("backend", action, user, properties);
    }

    // Front-end actions relayed through the API, such as page views, keep system=front.
    void track(String system, String action, CommunityUser user, Map<String, Object> properties) {
        WaterBoxSender target = sender.getIfAvailable();
        if (target == null) {
            return;
        }

        RequestAttributes request = RequestContextHolder.getRequestAttributes();
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("timestamp", System.currentTimeMillis());
        event.put("log_id", UUID.randomUUID().toString().replace("-", ""));
        event.put("session_id", attribute(request, TrackingFilter.SESSION_ID, "srv-" + UUID.randomUUID()));
        event.put("span_id", attribute(request, TrackingFilter.SPAN_ID, UUID.randomUUID().toString()));
        event.put("pre_span_id", attribute(request, TrackingFilter.PRE_SPAN_ID, null));
        event.put("system", system);
        event.put("theme", THEME_COMMUNITY);
        event.put("action", action);
        event.put("user_id", user.getId());
        event.put("team_id", null);
        event.put("username", limit(user.getNickname()));
        event.put("env", env);
        event.put("properties", properties);

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    dispatch(target, event);
                }
            });
        } else {
            dispatch(target, event);
        }
    }

    private void dispatch(WaterBoxSender target, Map<String, Object> event) {
        try {
            target.send(event);
        } catch (RuntimeException exception) {
            log.warn("water-box event dropped: action={} log_id={}", event.get("action"), event.get("log_id"), exception);
        }
    }

    private static String attribute(RequestAttributes request, String name, String fallback) {
        Object value = request == null ? null : request.getAttribute(name, RequestAttributes.SCOPE_REQUEST);
        return value instanceof String text ? text : fallback;
    }

    static String limit(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String stripped = value.strip();
        return stripped.length() <= MAX_STRING_LENGTH ? stripped : stripped.substring(0, MAX_STRING_LENGTH);
    }
}

interface WaterBoxSender {
    void send(Map<String, Object> event);
}

@Component
@ConditionalOnProperty(name = "water-box.enabled", havingValue = "true", matchIfMissing = true)
class HttpWaterBoxSender implements WaterBoxSender {
    private static final Logger log = LoggerFactory.getLogger(HttpWaterBoxSender.class);
    private static final int MAX_ATTEMPTS = 3;

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .build();
    private final JsonMapper jsonMapper;
    private final URI endpoint;

    HttpWaterBoxSender(JsonMapper jsonMapper,
                       @Value("${water-box.endpoint:http://127.0.0.1:8080/api/v1/events}") String endpoint) {
        this.jsonMapper = jsonMapper;
        this.endpoint = URI.create(endpoint);
    }

    @Override
    public void send(Map<String, Object> event) {
        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(Duration.ofSeconds(3))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonMapper.writeValueAsString(event)))
                .build();
        attempt(request, event, 1);
    }

    // Retries reuse the same body, so water-box consumers can de-duplicate by log_id.
    private void attempt(HttpRequest request, Map<String, Object> event, int attempt) {
        client.sendAsync(request, HttpResponse.BodyHandlers.ofString()).whenComplete((response, error) -> {
            boolean retryable = error != null || response.statusCode() >= 500;
            if (!retryable && response.statusCode() == HttpStatus.ACCEPTED.value()) {
                log.debug("water-box accepted action={} log_id={}", event.get("action"), event.get("log_id"));
                return;
            }
            if (retryable && attempt < MAX_ATTEMPTS) {
                CompletableFuture.delayedExecutor(200L * attempt, TimeUnit.MILLISECONDS)
                        .execute(() -> attempt(request, event, attempt + 1));
                return;
            }
            log.warn("water-box rejected action={} log_id={} status={} error={}",
                    event.get("action"), event.get("log_id"),
                    response == null ? "-" : response.statusCode(),
                    error == null ? response.body() : error.toString());
        });
    }
}

/**
 * Carries the browser session and request chain ids into backend events.
 */
@Component
class TrackingFilter extends OncePerRequestFilter {
    static final String SESSION_ID = TrackingFilter.class.getName() + ".session";
    static final String SPAN_ID = TrackingFilter.class.getName() + ".span";
    static final String PRE_SPAN_ID = TrackingFilter.class.getName() + ".preSpan";

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String sessionId = WaterBoxTracker.limit(request.getHeader("X-Session-Id"));
        String spanId = UUID.randomUUID().toString();
        request.setAttribute(SESSION_ID, sessionId == null ? "srv-" + UUID.randomUUID() : sessionId);
        request.setAttribute(SPAN_ID, spanId);
        String preSpanId = WaterBoxTracker.limit(request.getHeader("X-Span-Id"));
        if (preSpanId != null) {
            request.setAttribute(PRE_SPAN_ID, preSpanId);
        }
        response.setHeader("X-Span-Id", spanId);
        chain.doFilter(request, response);
    }
}
