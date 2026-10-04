package com.hls.audit.apiaccess;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.auth.AuthDtos;
import com.hls.identity.clientcontext.ApiAccessRecorded;
import com.hls.identity.clientcontext.ClientContext;
import com.hls.identity.clientcontext.ClientSource;
import com.hls.identity.clientcontext.LocationCapture;
import com.hls.identity.user.Role;
import com.hls.support.MobileTestBase;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpMethod;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Spec 018 T090 (US4, FR-023a): every request from the Android app, reads included, leaves one API
 * Access entry with its route pattern, status and location, and nothing else about the request.
 */
class ApiAccessRecordingTest extends MobileTestBase {

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private record Account(String phone, UUID id, String token) {}

    private Account androidSignIn() {
        String phone = nextPhone();
        UUID id = userAdminService
                .createUser("Access Teacher " + phone, phone, Set.of(Role.TEACHER), null, PASSWORD)
                .getId();
        Raw login = postRaw("/api/v1/auth/login", new AuthDtos.LoginRequest(phone, PASSWORD), ANDROID);
        return new Account(phone, id, login.string("accessToken"));
    }

    private List<Map<String, Object>> entriesFor(UUID userId, int expected) {
        return awaitRows("SELECT * FROM api_access_entry WHERE user_id = ? ORDER BY occurred_at", expected, userId);
    }

    @Test
    void aReadFromTheAppIsRecordedWithItsLocationRouteAndStatus() {
        Account account = androidSignIn();

        Raw read = getRaw("/api/v1/me/profile", account.token(), "android/1.0.0", withLocation(12.9, 77.5, 12.0));

        assertThat(read.status()).isEqualTo(200);
        Map<String, Object> entry = entriesFor(account.id(), 1).get(0);
        assertThat(entry.get("http_method")).isEqualTo("GET");
        assertThat(entry.get("route_template")).isEqualTo("/api/v1/me/profile");
        assertThat(((Number) entry.get("status_code")).intValue()).isEqualTo(200);
        assertThat(entry.get("source")).isEqualTo("ANDROID");
        assertThat(entry.get("app_version")).isEqualTo("1.0.0");
        assertThat(entry.get("location_status")).isEqualTo("AVAILABLE");
        assertThat((BigDecimal) entry.get("latitude")).isEqualByComparingTo("12.9");
        assertThat(entry.get("session_id")).isNotNull();
    }

    @Test
    void everyRequestGetsItsOwnEntryIncludingFailedAndUnauthenticatedOnes() {
        String phone = nextPhone();
        userAdminService.createUser("Failed Teacher " + phone, phone, Set.of(Role.TEACHER), null, PASSWORD);

        // A wrong password: no user is signed in, but the attempt is still recorded.
        postRaw("/api/v1/auth/login", new AuthDtos.LoginRequest(phone, "wrong-password-1"), ANDROID, withLocation(1, 2, 3));
        // A request security rejects before the application sees it.
        getRaw("/api/v1/me/profile", null, ANDROID, Map.of("X-HLS-Location-Status", "SERVICES_OFF"));

        List<Map<String, Object>> rows = awaitRows(
                "SELECT * FROM api_access_entry WHERE user_id IS NULL AND source = 'ANDROID'"
                        + " AND ((http_method = 'POST' AND route_template = '/api/v1/auth/login' AND status_code = 401)"
                        + " OR (http_method = 'GET' AND status_code = 401 AND location_status = 'SERVICES_OFF'))",
                2);
        assertThat(rows).hasSizeGreaterThanOrEqualTo(2);
        assertThat(rows.stream().map(r -> r.get("route_template")).toList()).contains("/api/v1/auth/login", "UNMATCHED");
    }

    @Test
    void theRouteIsThePatternNeverTheQueryStringOrAnIdFromThePath() {
        Account account = androidSignIn();
        // A second (web) session of the same user, which the Android session then ends.
        postRaw("/api/v1/auth/login", new AuthDtos.LoginRequest(account.phone(), PASSWORD), null);
        String otherSessionId = sessionIdOtherThanCurrent(account.token());

        getRaw("/api/v1/me/profile?secret=top-secret-value&token=abc123", account.token(), ANDROID, Map.of());
        callRaw(HttpMethod.DELETE, "/api/v1/me/sessions/" + otherSessionId, account.token(), null, ANDROID, Map.of());

        List<Map<String, Object>> entries = entriesFor(account.id(), 2);
        String everything = entries.stream().map(Object::toString).reduce("", String::concat);
        assertThat(everything).doesNotContain("top-secret-value").doesNotContain("abc123").doesNotContain(otherSessionId);
        assertThat(entries.stream().map(e -> e.get("route_template").toString()).toList())
                .noneMatch(route -> route.contains(otherSessionId) || route.contains("?"));
        assertThat(entries.stream().map(e -> e.get("route_template")).toList())
                .contains("/api/v1/me/profile", "/api/v1/me/sessions/{sessionId}");
    }

    private String sessionIdOtherThanCurrent(String token) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> sessions = (List<Map<String, Object>>) (List<?>) org.springframework.boot.json
                .JsonParserFactory.getJsonParser()
                .parseList(get("/api/v1/me/sessions", token).body());
        return sessions.stream()
                .filter(s -> Boolean.FALSE.equals(s.get("current")))
                .map(s -> s.get("id").toString())
                .findFirst()
                .orElseThrow();
    }

    @Test
    void noBodyHeaderOrQueryStringIsStoredAnywhereInTheRow() {
        Account account = androidSignIn();
        getRaw("/api/v1/me/profile?x=1", account.token(), ANDROID, Map.of("X-Custom-Secret", "do-not-store-me"));

        Map<String, Object> entry = entriesFor(account.id(), 1).get(0);

        // The table has no column that could hold content, headers or query strings.
        List<String> columns = jdbc.queryForList(
                "SELECT column_name FROM information_schema.columns WHERE table_name = 'api_access_entry'", String.class);
        assertThat(columns)
                .containsExactlyInAnyOrder(
                        "id", "occurred_at", "source_event_id", "user_id", "session_id", "http_method", "route_template",
                        "status_code", "source", "app_version", "location_status", "latitude", "longitude",
                        "accuracy_meters", "location_captured_at");
        assertThat(entry.toString()).doesNotContain("do-not-store-me").doesNotContain(account.token());
    }

    @Test
    void theAppConfigRequestAndWebRequestsAreNotRecorded() {
        Account account = androidSignIn();
        getRaw("/api/v1/mobile/app-config", null, ANDROID, withLocation(1, 2, 3));
        // A web request by the same user (no client header) is never recorded.
        getRaw("/api/v1/me/profile", account.token(), null, Map.of("X-HLS-Location", locationHeader(1, 2, 3, Instant.now())));
        // A marker Android request proves the consumer has caught up with everything before it.
        getRaw("/api/v1/me/access-model", account.token(), ANDROID, Map.of());

        List<Map<String, Object>> entries = entriesFor(account.id(), 1);

        assertThat(entries.stream().map(e -> e.get("route_template")).toList()).containsExactly("/api/v1/me/access-model");
        Integer appConfig = jdbc.queryForObject(
                "SELECT COUNT(*) FROM api_access_entry WHERE route_template = '/api/v1/mobile/app-config'", Integer.class);
        assertThat(appConfig).isZero();
    }

    @Test
    void aRedeliveredEventCreatesNoDuplicateEntry() throws InterruptedException {
        UUID eventId = UUID.randomUUID();
        ApiAccessRecorded event = new ApiAccessRecorded(
                eventId,
                Instant.now(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "GET",
                "/api/v1/me/profile",
                200,
                new ClientContext(ClientSource.ANDROID, "1.0.0", LocationCapture.available(1, 2, 3, Instant.now()), false));
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        tx.executeWithoutResult(status -> eventPublisher.publishEvent(event));
        awaitRows("SELECT * FROM api_access_entry WHERE source_event_id = ?", 1, eventId);
        tx.executeWithoutResult(status -> eventPublisher.publishEvent(event));
        Thread.sleep(1500);

        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM api_access_entry WHERE source_event_id = ?", Integer.class, eventId);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void aNotFoundRequestIsRecordedWithItsStatus() {
        Account account = androidSignIn();

        Raw missing = getRaw("/api/v1/does-not-exist", account.token(), ANDROID, Map.of());

        assertThat(missing.status()).isEqualTo(404);
        List<Map<String, Object>> entries =
                awaitRows("SELECT * FROM api_access_entry WHERE user_id = ? AND status_code = 404", 1, account.id());
        assertThat(entries).isNotEmpty();
    }
}
