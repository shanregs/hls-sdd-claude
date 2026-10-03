package com.hls.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.hls.identity.user.Role;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** Helpers shared by the spec 005 integration tests: build Zones/Places/Schools through the API. */
public abstract class MasterDataTestBase extends IntegrationTestBase {

    private static final AtomicLong NAMES = new AtomicLong();
    private static final AtomicLong PINS = new AtomicLong(100000);

    @Autowired
    protected JdbcTemplate jdbc;

    protected static String uniqueName(String prefix) {
        return prefix + " " + NAMES.incrementAndGet() + "-" + UUID.randomUUID().toString().substring(0, 6);
    }

    protected static String nextPin() {
        return Long.toString(PINS.incrementAndGet());
    }

    protected UUID zone(String adminToken) {
        Resp resp = post("/api/v1/zones", adminToken, Map.of("name", uniqueName("Zone")));
        assertThat(resp.status()).as(resp.body()).isEqualTo(201);
        return resp.id();
    }

    protected UUID place(String adminToken, UUID zoneId) {
        Resp resp = post(
                "/api/v1/zones/" + zoneId + "/places",
                adminToken,
                Map.of("name", uniqueName("Place"), "pinCode", nextPin()));
        assertThat(resp.status()).as(resp.body()).isEqualTo(201);
        return resp.id();
    }

    protected UUID school(String adminToken, UUID placeId) {
        Resp resp = post(
                "/api/v1/schools",
                adminToken,
                Map.of(
                        "name", uniqueName("School"),
                        "placeId", placeId,
                        "address", "1 Main Road",
                        "contactPerson", "Principal",
                        "contactPhone", "9000000000",
                        "billingContact", "accounts@school.example"));
        assertThat(resp.status()).as(resp.body()).isEqualTo(201);
        return resp.id();
    }

    /** A School in a fresh Zone and Place; returns {zoneId, placeId, schoolId}. */
    protected UUID[] schoolInNewZone(String adminToken) {
        UUID zoneId = zone(adminToken);
        UUID placeId = place(adminToken, zoneId);
        return new UUID[] {zoneId, placeId, school(adminToken, placeId)};
    }

    /** A signed-in Manager user together with their Manager record. */
    public record ManagerCtx(Signed signed, UUID managerId) {
        public String token() {
            return signed.token();
        }
    }

    /** Creates a Manager user and record, assigned to the given Zones (admin does the work). */
    protected ManagerCtx newManager(String adminToken, UUID... zoneIds) {
        Signed signed = signInAs(Role.MANAGER);
        Resp created = post("/api/v1/managers", adminToken, Map.of("userId", signed.userId()));
        assertThat(created.status()).as(created.body()).isEqualTo(201);
        UUID managerId = created.id();
        if (zoneIds.length > 0) {
            assignZones(adminToken, managerId, zoneIds);
        }
        return new ManagerCtx(signed, managerId);
    }

    protected Resp assignZonesRaw(String adminToken, UUID managerId, UUID... zoneIds) {
        long version = ((Number) get("/api/v1/managers/" + managerId, adminToken).map().get("version")).longValue();
        return put(
                "/api/v1/managers/" + managerId + "/zones",
                adminToken,
                Map.of("zoneIds", java.util.List.of(zoneIds), "version", version));
    }

    protected void assignZones(String adminToken, UUID managerId, UUID... zoneIds) {
        Resp resp = assignZonesRaw(adminToken, managerId, zoneIds);
        assertThat(resp.status()).as(resp.body()).isEqualTo(200);
    }

    protected Resp assignSchoolManagerRaw(String adminToken, UUID schoolId, UUID managerId) {
        java.util.Map<String, Object> body = new java.util.HashMap<>();
        body.put("managerId", managerId);
        return put("/api/v1/schools/" + schoolId + "/manager", adminToken, body);
    }

    protected void assignSchoolManager(String adminToken, UUID schoolId, UUID managerId) {
        Resp resp = assignSchoolManagerRaw(adminToken, schoolId, managerId);
        assertThat(resp.status()).as(resp.body()).isEqualTo(204);
    }

    protected UUID teacher(String adminToken) {
        Resp resp = post(
                "/api/v1/teachers",
                adminToken,
                Map.of("name", uniqueName("Teacher"), "phone", "9444444444", "status", "ACTIVE"));
        assertThat(resp.status()).as(resp.body()).isEqualTo(201);
        return resp.id();
    }

    protected Resp placeTeacherRaw(String adminToken, UUID teacherId, UUID schoolId, java.time.LocalDate on) {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("schoolId", schoolId);
        body.put("effectiveOn", on == null ? null : on.toString());
        return post("/api/v1/teachers/" + teacherId + "/placements", adminToken, body);
    }

    protected void placeTeacher(String adminToken, UUID teacherId, UUID schoolId) {
        Resp resp = placeTeacherRaw(adminToken, teacherId, schoolId, null);
        assertThat(resp.status()).as(resp.body()).isEqualTo(200);
    }

    /** Waits for a Change History row (the audit event is delivered after commit). */
    protected void assertChangeRecorded(String adminToken, String entityType, Object entityId, String field) {
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            Resp resp = get(
                    "/api/v1/audit/change-history?entityType=" + entityType + "&entityId=" + entityId + "&size=100",
                    adminToken);
            assertThat(resp.status()).isEqualTo(200);
            assertThat(resp.body()).contains("\"field\":\"" + field + "\"");
        });
    }
}
