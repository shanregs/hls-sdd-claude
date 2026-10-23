package com.hls.transfer;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import com.hls.schoolbilling.SchoolContractsTestBase;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Helpers for the spec 027 tests: two Managers with their Schools, placed Teachers, an unplaced Teacher, a School with an MoU
 * and the transfers API.
 */
public abstract class TransferTestBase extends SchoolContractsTestBase {

    protected static final String API = "/api/v1/transfers";

    /** Manager A runs A1, Manager B runs B1; T1 is placed at A1 and T3 is active and unplaced. */
    protected record World(
            String admin,
            Signed directorSigned,
            ManagerCtx managerA,
            ManagerCtx managerB,
            UUID zoneA,
            UUID schoolA1,
            UUID zoneB,
            UUID schoolB1,
            UUID t1,
            UUID t3) {

        String director() {
            return directorSigned.token();
        }
    }

    protected World world() {
        String admin = signInAs(Role.ADMIN).token();
        Signed director = signInAs(Role.DIRECTOR);
        UUID[] a = schoolInNewZone(admin);
        UUID[] b = schoolInNewZone(admin);
        ManagerCtx managerA = newManager(admin, a[0]);
        assignSchoolManager(admin, a[2], managerA.managerId());
        ManagerCtx managerB = newManager(admin, b[0]);
        assignSchoolManager(admin, b[2], managerB.managerId());
        UUID t1 = teacher(admin);
        placeTeacher(admin, t1, a[2]);
        UUID t3 = teacher(admin);
        return new World(admin, director, managerA, managerB, a[0], a[2], b[0], b[2], t1, t3);
    }

    protected LocalDate in(int days) {
        return LocalDate.now(java.time.ZoneId.of("Asia/Kolkata")).plusDays(days);
    }

    protected Map<String, Object> transferBody(UUID teacher, UUID toSchool, LocalDate on) {
        Map<String, Object> body = new HashMap<>();
        body.put("teacherId", teacher);
        body.put("toSchoolId", toSchool);
        body.put("effectiveOn", on.toString());
        body.put("reasonCategory", "MANAGER_REBALANCING");
        return body;
    }

    protected Map<String, Object> replacementBody(UUID teacher, UUID replacement, LocalDate on) {
        Map<String, Object> body = new HashMap<>();
        body.put("teacherId", teacher);
        body.put("replacementTeacherId", replacement);
        body.put("effectiveOn", on.toString());
        body.put("reasonCategory", "AFTER_REVIEW");
        return body;
    }

    protected Resp raiseTransfer(String token, UUID teacher, UUID toSchool, LocalDate on) {
        return post(API + "/transfers", token, transferBody(teacher, toSchool, on));
    }

    protected UUID newTransfer(String token, UUID teacher, UUID toSchool, LocalDate on) {
        Resp resp = raiseTransfer(token, teacher, toSchool, on);
        assertThat(resp.status()).as(resp.body()).isEqualTo(201);
        return resp.id();
    }

    protected UUID newReplacement(String token, UUID teacher, UUID replacement, LocalDate on) {
        Resp resp = post(API + "/replacements", token, replacementBody(teacher, replacement, on));
        assertThat(resp.status()).as(resp.body()).isEqualTo(201);
        return resp.id();
    }

    protected long versionOf(String token, UUID request) {
        return ((Number) get(API + "/" + request, token).map().get("version")).longValue();
    }

    protected Resp approve(String token, UUID request) {
        return post(API + "/" + request + "/approve", token, Map.of("version", versionOf(token, request)));
    }

    protected Resp reject(String token, UUID request, String reason) {
        Map<String, Object> body = new HashMap<>();
        body.put("version", versionOf(token, request));
        body.put("reason", reason);
        return post(API + "/" + request + "/reject", token, body);
    }

    protected Resp cancel(String token, UUID request, String reason) {
        Map<String, Object> body = new HashMap<>();
        body.put("version", versionOf(token, request));
        body.put("reason", reason);
        return post(API + "/" + request + "/cancel", token, body);
    }

    protected String statusOfRequest(String token, UUID request) {
        return (String) get(API + "/" + request, token).map().get("status");
    }

    /** Placement rows of the Teacher: school, starts_on, ends_on for ACTIVE rows, oldest first. */
    protected List<Map<String, Object>> rows(UUID teacher) {
        return jdbc.queryForList(
                "select school_id, position_id, starts_on, ends_on from contract_assignment where teacher_id = ? and status = 'ACTIVE' order by starts_on",
                teacher);
    }

    /** The School the Teacher is placed at on the date according to the ACTIVE rows, or null. */
    protected UUID schoolOn(UUID teacher, LocalDate date) {
        List<UUID> found = jdbc.queryForList(
                "select school_id from contract_assignment where teacher_id = ? and status = 'ACTIVE' and starts_on <= ?"
                        + " and (ends_on is null or ends_on >= ?)",
                UUID.class,
                teacher,
                date,
                date);
        assertThat(found.size()).as("Teacher " + teacher + " on " + date).isLessThanOrEqualTo(1);
        return found.isEmpty() ? null : found.get(0);
    }

    /** A School with an MoU of the given number of positions starting today; returns {schoolId, managerCtx...} via Fixture. */
    protected Fixture schoolWithMou(World w, int positions) {
        Fixture f = fixture(w.admin());
        String[] salaries = new String[positions];
        java.util.Arrays.fill(salaries, "20000.00");
        Resp created = createContract(w.director(), f.schoolId(), perTeacherBody(f, w.directorSigned().userId(), salaries));
        assertThat(created.status()).as(created.body()).isEqualTo(201);
        return f;
    }
}
