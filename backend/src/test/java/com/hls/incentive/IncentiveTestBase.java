package com.hls.incentive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.hls.identity.user.Role;
import com.hls.schoolbilling.MouTestBase;
import com.hls.attendance.api.WorkingCalendar;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Helpers for the spec 030 tests: a School with an ACTIVE MoU and a Zone Manager (spec 031 helpers), a won prospect for it
 * owned by someone chosen by the test, the MoU approved through the real endpoints, and waits for the after-commit
 * listeners.
 */
public abstract class IncentiveTestBase extends MouTestBase {

    protected static final String API = "/api/v1/incentives";

    protected static final String RUNS = "/api/v1/payroll/runs";

    @Autowired
    protected JdbcTemplate db;

    @Autowired
    protected WorkingCalendar workingCalendar;

    /** Inserts a won prospect linked to the School, owned by the user. */
    protected void wonProspect(UUID zoneId, UUID schoolId, UUID ownerUserId, UUID wonBy) {
        String name = "Won " + UUID.randomUUID();
        db.update(
                "insert into marketing_prospect (id, name, zone_id, name_key, owner_user_id, stage, won_at, won_by,"
                        + " school_id, version, created_by, created_at) values (?, ?, ?, ?, ?, 'FINAL_STAGE', now(), ?, ?, 0, ?, now())",
                UUID.randomUUID(), name, zoneId, name.toLowerCase(), ownerUserId, wonBy, schoolId, wonBy);
    }

    /** Submits as Admin and approves as the Director through the real endpoints. */
    protected void approveMou(Mou m) {
        Resp submitted = submit(m.admin(), m.contractId());
        assertThat(submitted.status()).as(submitted.body()).isEqualTo(200);
        Resp approved = approve(m.director().token(), m.contractId());
        assertThat(approved.status()).as(approved.body()).isEqualTo(200);
    }

    /** A School whose MoU is approved and whose won prospect is owned by the given user. */
    protected Mou approvedMouOwnedBy(UUID ownerUserId) {
        Mou m = mou();
        wonProspect(m.f().zoneId(), m.f().schoolId(), ownerUserId, m.adminUserId());
        approveMou(m);
        return m;
    }

    /** Waits for the after-commit listeners of the events published so far (does not fail on a stuck one). */
    protected void settle() {
        try {
            await().atMost(Duration.ofSeconds(15))
                    .pollInterval(Duration.ofMillis(100))
                    .until(() -> db.queryForObject(
                                    "select count(*) from event_publication where completion_date is null", Integer.class)
                            == 0);
        } catch (RuntimeException ignored) {
            // a failed listener from another test must not fail this one; the assertions that follow decide
        }
    }

    protected Map<String, Object> incentiveOf(UUID schoolId) {
        List<Map<String, Object>> rows = db.queryForList("select * from incentive where school_id = ?", schoolId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** Waits until the School has an incentive and returns its row. */
    protected Map<String, Object> awaitIncentive(UUID schoolId) {
        await().atMost(Duration.ofSeconds(20)).pollInterval(Duration.ofMillis(100)).until(() -> incentiveOf(schoolId) != null);
        return incentiveOf(schoolId);
    }

    protected Map<String, Object> noEarnerOf(UUID schoolId) {
        List<Map<String, Object>> rows = db.queryForList("select * from no_earner where school_id = ?", schoolId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    protected Map<String, Object> awaitNoEarner(UUID schoolId) {
        await().atMost(Duration.ofSeconds(20)).pollInterval(Duration.ofMillis(100)).until(() -> noEarnerOf(schoolId) != null);
        return noEarnerOf(schoolId);
    }

    protected int incentiveRows(UUID schoolId) {
        return db.queryForObject("select count(*) from incentive where school_id = ?", Integer.class, schoolId);
    }

    protected List<String> eventKinds(Object incentiveId) {
        return db.queryForList(
                "select kind from incentive_event where incentive_id = ? order by seq", String.class, incentiveId);
    }

    protected UUID idOf(Map<String, Object> row) {
        return (UUID) row.get("id");
    }

    protected String adminToken() {
        return signInAs(Role.ADMIN).token();
    }

    protected Resp setAmount(String adminToken, int rupees) {
        return put("/api/v1/settings/system", adminToken, Map.of("values", Map.of("incentive.amount-per-school", Integer.toString(rupees))));
    }

    // ------------------------------------------------------------------ payroll helpers (the 013b tests' way)

    /** The month a run started now pays: the previous business month. */
    protected YearMonth payMonth() {
        return YearMonth.from(workingCalendar.businessToday()).minusMonths(1);
    }

    /** TRUNCATE does not fire the row triggers, so a test can start with clean run tables. */
    protected void resetRuns() {
        db.execute("truncate payroll_paid_month, payroll_payment_entry, payroll_payslip, payroll_run_event,"
                + " payroll_run_line_adjustment, payroll_adjustment, payroll_run_line, payroll_run");
    }

    /** Gives the Manager employment, a salary in the past and a locked, fully worked previous month. */
    protected void makePayable(String admin, ManagerCtx manager, String salary) {
        long version = ((Number) get("/api/v1/managers/" + manager.managerId(), admin).map().get("version")).longValue();
        Resp employed = put("/api/v1/managers/" + manager.managerId() + "/employment", admin, Map.of(
                "employeeId", "E" + Long.toString(System.nanoTime(), 36).toUpperCase(),
                "joiningDate", payMonth().atDay(1).minusMonths(3).toString(),
                "version", version));
        assertThat(employed.status()).as(employed.body()).isEqualTo(200);
        if (salary != null) {
            db.update(
                    "insert into person_salary (id, person_kind, person_id, amount, effective_on, note, recorded_by, recorded_at)"
                            + " values (?, 'MANAGER', ?, ?::numeric, ?, null, ?, ?)",
                    UUID.randomUUID(), manager.managerId(), salary, LocalDate.now().minusDays(400), UUID.randomUUID(),
                    Timestamp.from(Instant.now()));
            for (LocalDate date : workingCalendar.workingDays(null, payMonth().atDay(1), payMonth().atEndOfMonth())) {
                db.update(
                        "insert into manager_day (id, manager_id, day_date, day_type, check_in_at, check_in_lat, check_in_lng, version)"
                                + " values (?, ?, ?, 'OFFICE', ?, 12.97, 77.59, 0)",
                        UUID.randomUUID(), manager.managerId(), date,
                        Timestamp.from(date.atTime(9, 0).atZone(java.time.ZoneId.of("Asia/Kolkata")).toInstant()));
            }
            Resp locked = post("/api/v1/manager-attendance/months/" + payMonth() + "/lock", admin,
                    Map.of("managerId", manager.managerId().toString()));
            assertThat(locked.status()).as(locked.body()).isEqualTo(200);
        }
    }

    protected Map<String, Object> awaitComputed(String token, UUID runId) {
        long deadline = System.currentTimeMillis() + 180_000;
        while (true) {
            Resp resp = get(RUNS + "/" + runId, token);
            assertThat(resp.status()).as(resp.body()).isEqualTo(200);
            String state = (String) resp.map().get("computeState");
            if ("DONE".equals(state) || "FAILED".equals(state)) {
                return resp.map();
            }
            assertThat(System.currentTimeMillis()).as("the run did not finish: " + resp.map()).isLessThan(deadline);
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
    }

    protected UUID startAndCompute(String token) {
        Resp started = post(RUNS, token, Map.of("month", payMonth().toString()));
        assertThat(started.status()).as(started.body()).isEqualTo(202);
        Map<String, Object> done = awaitComputed(token, started.id());
        assertThat(done.get("computeState")).as(done.toString()).isEqualTo("DONE");
        return started.id();
    }

    protected void recompute(String token, UUID run) {
        assertThat(post(RUNS + "/" + run + "/recompute", token, Map.of()).status()).isEqualTo(202);
        awaitComputed(token, run);
    }

    /** The person's line in the run, or null. */
    protected Map<String, Object> lineOf(String token, UUID runId, UUID personId) {
        for (int page = 0; ; page++) {
            Resp resp = get(RUNS + "/" + runId + "/lines?size=200&page=" + page, token);
            assertThat(resp.status()).as(resp.body()).isEqualTo(200);
            List<Map<String, Object>> rows = content(resp);
            for (Map<String, Object> line : rows) {
                if (personId.toString().equals(line.get("personId"))) {
                    return line;
                }
            }
            if (rows.isEmpty() || (page + 1) * 200L >= total(resp)) {
                return null;
            }
        }
    }

    protected Resp submitRun(String token, UUID run) {
        return post(RUNS + "/" + run + "/submit", token, Map.of());
    }

    protected Resp approveRun(String token, UUID run) {
        return post(RUNS + "/" + run + "/approve", token, Map.of());
    }

    protected Resp returnRun(String token, UUID run, String reason) {
        return post(RUNS + "/" + run + "/return", token, Map.of("reason", reason));
    }
}
