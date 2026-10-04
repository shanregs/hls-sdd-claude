package com.hls.support;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.hls.identity.otp.SmsGateway;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.json.JsonParserFactory;
import org.springframework.jdbc.core.JdbcTemplate;

/** Shared helpers for the spec 018 mobile tests: raw calls with the Android client header. */
public abstract class MobileTestBase extends IntegrationTestBase {

    protected static final String ANDROID = "android/1.0.0";
    private static final Pattern CODE_PATTERN = Pattern.compile("code is (\\d{6})");

    @Autowired
    protected JdbcTemplate jdbc;

    private ListAppender<ILoggingEvent> smsAppender;

    /** One HTTP response, with the Set-Cookie headers kept separately. */
    public record Raw(int status, String body, List<String> setCookies) {
        public Map<String, Object> map() {
            return JsonParserFactory.getJsonParser().parseMap(body);
        }

        public String string(String key) {
            Object value = map().get(key);
            return value == null ? null : value.toString();
        }

        public boolean hasRenewalCookie() {
            return setCookies.stream().anyMatch(c -> c.startsWith("renewal_credential="));
        }
    }

    @BeforeEach
    void attachSmsLogCapture() {
        Logger logger = (Logger) LoggerFactory.getLogger(SmsGateway.DevStubSmsGateway.class);
        smsAppender = new ListAppender<>();
        smsAppender.setContext(logger.getLoggerContext());
        smsAppender.start();
        logger.addAppender(smsAppender);
        logger.setLevel(Level.INFO);
    }

    @AfterEach
    void detachSmsLogCapture() {
        ((Logger) LoggerFactory.getLogger(SmsGateway.DevStubSmsGateway.class)).detachAppender(smsAppender);
    }

    protected Raw postRaw(String uri, Object body, String clientHeader, Map<String, String> extraHeaders) {
        var spec = client.post().uri(uri);
        if (clientHeader != null) {
            spec.header("X-HLS-Client", clientHeader);
        }
        extraHeaders.forEach(spec::header);
        var ready = body != null ? spec.body(body) : spec;
        var result = ready.exchange().returnResult(String.class);
        List<String> cookies = result.getResponseHeaders().get("Set-Cookie");
        return new Raw(
                result.getStatus().value(), result.getResponseBody(), cookies == null ? List.of() : cookies);
    }

    protected Raw postRaw(String uri, Object body, String clientHeader) {
        return postRaw(uri, body, clientHeader, Map.of());
    }

    /** The six-digit code the dev SMS stub logged most recently. */
    protected String latestSmsCode() {
        var list = smsAppender.list;
        if (list.isEmpty()) {
            throw new AssertionError("no SMS code was logged");
        }
        Matcher matcher = CODE_PATTERN.matcher(list.get(list.size() - 1).getFormattedMessage());
        if (!matcher.find()) {
            throw new AssertionError("no code in: " + list.get(list.size() - 1).getFormattedMessage());
        }
        return matcher.group(1);
    }

    /** Polls until {@code check} is true: audit consumers run asynchronously after commit. */
    protected void awaitTrue(String description, Supplier<Boolean> check) {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (System.nanoTime() < deadline) {
            if (check.get()) {
                return;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError("interrupted while waiting for " + description);
            }
        }
        throw new AssertionError("timed out waiting for " + description);
    }

    protected int loginHistoryCount(java.util.UUID userId, String eventType, String outcomeLike) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM login_history_entry WHERE user_id = ? AND event_type = ? AND outcome LIKE ?",
                Integer.class,
                userId,
                eventType,
                outcomeLike);
        return count == null ? 0 : count;
    }

    protected int activeSessionCount(java.util.UUID userId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM session WHERE user_id = ? AND status = 'ACTIVE'", Integer.class, userId);
        return count == null ? 0 : count;
    }

    /** An {@code X-HLS-Location} header value for a position captured at {@code at}. */
    protected static String locationHeader(double lat, double lng, double accuracyMeters, java.time.Instant at) {
        return "lat=" + lat + ";lng=" + lng + ";acc=" + accuracyMeters + ";ts=" + at.toEpochMilli();
    }

    /** Headers an Android request sends with a captured position. */
    protected static Map<String, String> withLocation(double lat, double lng, double acc) {
        return Map.of("X-HLS-Location", locationHeader(lat, lng, acc, java.time.Instant.now()));
    }

    /** Polls until at least {@code expected} rows come back (audit consumers run after commit). */
    protected List<Map<String, Object>> awaitRows(String sql, int expected, Object... args) {
        List<Map<String, Object>> rows = List.of();
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (System.nanoTime() < deadline) {
            rows = jdbc.queryForList(sql, args);
            if (rows.size() >= expected) {
                return rows;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError("interrupted while waiting for rows");
            }
        }
        throw new AssertionError("expected " + expected + " rows but found " + rows.size() + " for: " + sql);
    }

    /** A signed-in Android-style GET with optional extra headers, returning the raw status and body. */
    protected Raw getRaw(String uri, String token, String clientHeader, Map<String, String> extraHeaders) {
        var spec = client.get().uri(uri);
        if (token != null) {
            spec.header("Authorization", "Bearer " + token);
        }
        if (clientHeader != null) {
            spec.header("X-HLS-Client", clientHeader);
        }
        extraHeaders.forEach(spec::header);
        var result = spec.exchange().returnResult(String.class);
        List<String> cookies = result.getResponseHeaders().get("Set-Cookie");
        return new Raw(result.getStatus().value(), result.getResponseBody(), cookies == null ? List.of() : cookies);
    }

    /** Any request with the Android client header and optional extra headers. */
    protected Raw callRaw(
            org.springframework.http.HttpMethod method,
            String uri,
            String token,
            Object body,
            String clientHeader,
            Map<String, String> extraHeaders) {
        var spec = client.method(method).uri(uri);
        if (token != null) {
            spec.header("Authorization", "Bearer " + token);
        }
        if (clientHeader != null) {
            spec.header("X-HLS-Client", clientHeader);
        }
        extraHeaders.forEach(spec::header);
        var ready = body != null ? spec.body(body) : spec;
        var result = ready.exchange().returnResult(String.class);
        List<String> cookies = result.getResponseHeaders().get("Set-Cookie");
        return new Raw(result.getStatus().value(), result.getResponseBody(), cookies == null ? List.of() : cookies);
    }
}
