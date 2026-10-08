package com.hls.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Helpers for the spec 005a tests: designations and the employment endpoints. */
public abstract class DesignationTestBase extends MasterDataTestBase {

    protected UUID designation(String adminToken, String kind) {
        Resp resp = post("/api/v1/designations", adminToken, Map.of("name", uniqueName("Role"), "kind", kind));
        assertThat(resp.status()).as(resp.body()).isEqualTo(201);
        return resp.id();
    }

    protected Resp designate(String token, UUID managerId, UUID designationId, Object effectiveOn) {
        return post(
                "/api/v1/managers/" + managerId + "/designation",
                token,
                Map.of("designationId", designationId, "effectiveOn", String.valueOf(effectiveOn)));
    }

    protected long managerVersion(String token, UUID managerId) {
        return ((Number) get("/api/v1/managers/" + managerId, token).map().get("version")).longValue();
    }

    protected long teacherVersion(String token, UUID teacherId) {
        return ((Number) get("/api/v1/teachers/" + teacherId, token).map().get("version")).longValue();
    }

    protected Resp employment(String token, UUID managerId, String employeeId, Object joining, Object exit) {
        Map<String, Object> body = new HashMap<>();
        body.put("employeeId", employeeId);
        body.put("joiningDate", joining == null ? null : joining.toString());
        body.put("exitDate", exit == null ? null : exit.toString());
        body.put("version", managerVersion(token, managerId));
        return put("/api/v1/managers/" + managerId + "/employment", token, body);
    }

    protected Resp teacherEmployment(String token, UUID teacherId, UUID designationId, String employeeId) {
        Map<String, Object> body = new HashMap<>();
        body.put("designationId", designationId);
        body.put("employeeId", employeeId);
        body.put("version", teacherVersion(token, teacherId));
        return put("/api/v1/teachers/" + teacherId + "/employment", token, body);
    }

    /** An id within 20 characters that is unique across the shared test database. */
    protected static String uniqueEmployeeId() {
        return "E" + Long.toString(System.nanoTime(), 36).toUpperCase();
    }

    /** The designations endpoint returns a bare array; present it in the {content} shape the shared helper expects. */
    protected static List<Map<String, Object>> rows(Resp list) {
        return content(new Resp(list.status(), "{\"content\":" + list.body() + "}"));
    }
}
