package com.hls.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.hls.attendance.api.WorkingCalendar;
import java.time.Duration;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;

/** Helpers for the spec 013a tests: salaries by designation, the policy, and the data payroll reads. */
public abstract class PayrollTestBase extends DesignationTestBase {

    protected static final String BASE = "/api/v1/salary-structures";

    @Autowired
    protected WorkingCalendar workingCalendar;

    protected LocalDate today() {
        return workingCalendar.businessToday();
    }

    protected LocalDate firstOfMonth() {
        return today().withDayOfMonth(1);
    }

    protected Resp recordSalary(String token, UUID designationId, Object amount, Object effectiveOn, String note) {
        Map<String, Object> body = new HashMap<>();
        body.put("amount", amount == null ? null : String.valueOf(amount));
        body.put("effectiveOn", effectiveOn == null ? null : String.valueOf(effectiveOn));
        body.put("note", note);
        return post(BASE + "/designations/" + designationId + "/salaries", token, body);
    }

    protected Resp recordSalaryOk(String token, UUID designationId, Object amount, Object effectiveOn) {
        Resp resp = recordSalary(token, designationId, amount, effectiveOn, null);
        assertThat(resp.status()).as(resp.body()).isEqualTo(201);
        return resp;
    }

    protected Resp recordPolicy(
            String token, Object effectiveOn, Object fraction, String rounding, String lopDivisor, String payMonth, String note) {
        Map<String, Object> body = new HashMap<>();
        body.put("effectiveOn", effectiveOn == null ? null : String.valueOf(effectiveOn));
        body.put("lopDivisor", lopDivisor);
        body.put("halfDayFraction", fraction == null ? null : String.valueOf(fraction));
        body.put("rounding", rounding);
        body.put("payMonth", payMonth);
        body.put("note", note);
        return post(BASE + "/policy", token, body);
    }

    /** A policy version with the standard divisor and pay month; only the fraction, rounding and date vary. */
    protected Resp recordPolicy(String token, Object effectiveOn, Object fraction, String rounding) {
        return recordPolicy(token, effectiveOn, fraction, rounding, "WORKING_DAYS", "CALENDAR_MONTH", null);
    }

    @SuppressWarnings("unchecked")
    protected static List<Map<String, Object>> historyOf(Resp detail) {
        return (List<Map<String, Object>>) detail.map().get("history");
    }

    @SuppressWarnings("unchecked")
    protected Map<String, Object> entryOf(String token, UUID designationId) {
        Resp list = get(BASE + "/designations?includeRetired=true", token);
        assertThat(list.status()).as(list.body()).isEqualTo(200);
        return rows(list).stream()
                .filter(r -> designationId.toString().equals(r.get("designationId")))
                .findFirst()
                .orElseThrow();
    }

    /** The Change History entries of one entity, once at least {@code atLeast} have been delivered (after commit). */
    protected List<Map<String, Object>> changeEntries(String adminToken, String entityType, Object entityId, int atLeast) {
        java.util.concurrent.atomic.AtomicReference<List<Map<String, Object>>> found =
                new java.util.concurrent.atomic.AtomicReference<>(List.of());
        await().atMost(Duration.ofSeconds(8)).untilAsserted(() -> {
            Resp resp = get(
                    "/api/v1/audit/change-history?entityType=" + entityType + "&entityId=" + entityId + "&size=100",
                    adminToken);
            assertThat(resp.status()).isEqualTo(200);
            found.set(content(resp));
            assertThat(found.get()).hasSizeGreaterThanOrEqualTo(atLeast);
        });
        return found.get();
    }

    /** How many Change History entries one entity has right now. */
    protected int changeCount(String adminToken, String entityType, Object entityId) {
        Resp resp = get(
                "/api/v1/audit/change-history?entityType=" + entityType + "&entityId=" + entityId + "&size=100",
                adminToken);
        return content(resp).size();
    }
}
