package com.hls.schoolbilling;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import com.hls.support.MasterDataTestBase;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Helpers for the spec 012 integration tests: a School with a Zone Manager, MoU request bodies, mapping. */
public abstract class SchoolContractsTestBase extends MasterDataTestBase {

    protected static final String BASE = "/api/v1/school-contracts";

    protected final LocalDate today = LocalDate.now();

    /** A School in a fresh Zone with its Zone Manager (user, record) assigned. */
    protected record Fixture(UUID zoneId, UUID schoolId, ManagerCtx manager) {}

    protected Fixture fixture(String adminToken) {
        UUID[] ids = schoolInNewZone(adminToken);
        ManagerCtx manager = newManager(adminToken, ids[0]);
        assignSchoolManager(adminToken, ids[2], manager.managerId());
        return new Fixture(ids[0], ids[2], manager);
    }

    /** A School with no Manager at all. */
    protected UUID schoolWithoutManager(String adminToken) {
        return schoolInNewZone(adminToken)[2];
    }

    protected Signed director() {
        return signInAs(Role.DIRECTOR);
    }

    protected static Map<String, Object> schoolSigner(String name, String designation) {
        return Map.of("name", name, "designation", designation);
    }

    protected static Map<String, Object> hlsSigner(UUID userId, String designation) {
        return Map.of("userId", userId, "designation", designation);
    }

    /** A same-salary-for-all MoU body starting today, signed yesterday, signed by the Zone Manager and a Director. */
    protected Map<String, Object> sameSalaryBody(Fixture f, UUID directorUserId, int count, String rate) {
        Map<String, Object> body = new HashMap<>();
        body.put("teacherCount", count);
        body.put("salaryMode", "SAME_FOR_ALL");
        body.put("rate", rate);
        body.put("startsOn", today.toString());
        body.put("signedOn", today.minusDays(1).toString());
        body.put("schoolSignatories", List.of(schoolSigner("R. Kumar", "Principal")));
        body.put(
                "hlsSignatories",
                List.of(hlsSigner(f.manager().signed().userId(), "Zone Manager"), hlsSigner(directorUserId, "Director")));
        return body;
    }

    /** Moves the contract's start (and its signing) to {@code daysAgo} days before today. */
    protected Map<String, Object> startedDaysAgo(Map<String, Object> body, int daysAgo) {
        body.put("startsOn", today.minusDays(daysAgo).toString());
        body.put("signedOn", today.minusDays(daysAgo + 1).toString());
        return body;
    }

    /** A different-salary MoU body with one salary per position. */
    protected Map<String, Object> perTeacherBody(Fixture f, UUID directorUserId, String... salaries) {
        Map<String, Object> body = sameSalaryBody(f, directorUserId, salaries.length, "1.00");
        body.remove("rate");
        body.put("salaryMode", "PER_TEACHER");
        List<Map<String, Object>> positions = new ArrayList<>();
        for (int i = 0; i < salaries.length; i++) {
            positions.add(Map.of("title", "Position " + (i + 1), "salary", salaries[i]));
        }
        body.put("positions", positions);
        return body;
    }

    protected Resp createContract(String token, UUID schoolId, Map<String, Object> body) {
        return post(BASE + "/schools/" + schoolId + "/contracts", token, body);
    }

    protected Resp schoolContracts(String token, UUID schoolId) {
        return get(BASE + "/schools/" + schoolId, token);
    }

    /** Contract ids of the School, newest first. */
    @SuppressWarnings("unchecked")
    protected List<Map<String, Object>> contractsOf(String token, UUID schoolId) {
        Resp resp = schoolContracts(token, schoolId);
        assertThat(resp.status()).as(resp.body()).isEqualTo(200);
        return (List<Map<String, Object>>) resp.map().get("contracts");
    }

    @SuppressWarnings("unchecked")
    protected static List<Map<String, Object>> positionsOf(Map<String, Object> contract) {
        return (List<Map<String, Object>>) contract.get("positions");
    }

    protected UUID contractId(Resp created) {
        assertThat(created.status()).as(created.body()).isEqualTo(201);
        return created.id();
    }

    /** Assigns a Teacher to the School through the placement endpoint (admin), optionally to a position. */
    protected Resp assign(String token, UUID teacherId, UUID schoolId, UUID positionId, LocalDate on) {
        Map<String, Object> body = new HashMap<>();
        body.put("schoolId", schoolId);
        body.put("positionId", positionId);
        body.put("effectiveOn", on == null ? null : on.toString());
        return post("/api/v1/teachers/" + teacherId + "/placements", token, body);
    }
}
