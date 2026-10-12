package com.hls.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.leave.LeaveTestBase;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Helpers shared by the spec 017a integration tests. */
public abstract class SubstitutionTestBase extends LeaveTestBase {

    protected static final String SUBS = "/api/v1/substitutions";

    /** Marks the Teacher absent as the given supervisor and returns the response. */
    protected Resp markAbsent(String token, UUID teacher, LocalDate date, double value) {
        return markAs(token, teacher, date, "A", value);
    }

    protected Resp markAs(String token, UUID teacher, LocalDate date, String code, double value) {
        return put(
                "/api/v1/attendance/teachers/" + teacher + "/marks/" + date,
                token,
                Map.of("statusCode", code, "dayValue", value));
    }

    protected void absent(String token, UUID teacher, LocalDate date) {
        Resp resp = markAbsent(token, teacher, date, 1);
        assertThat(resp.status()).as(resp.body()).isEqualTo(200);
    }

    protected List<Map<String, Object>> requirementRows(UUID teacher, LocalDate date) {
        return jdbc.queryForList(
                "select * from substitution_requirement where teacher_id = ? and duty_date = ?", teacher, date);
    }

    protected Map<String, Object> requirementRow(UUID teacher, LocalDate date) {
        List<Map<String, Object>> rows = requirementRows(teacher, date);
        assertThat(rows).as("requirement of " + teacher + " on " + date).hasSize(1);
        return rows.get(0);
    }

    protected UUID requirementId(UUID teacher, LocalDate date) {
        return (UUID) requirementRow(teacher, date).get("id");
    }

    protected long version(UUID requirementId) {
        return jdbc.queryForObject("select version from substitution_requirement where id = ?", Long.class, requirementId);
    }

    protected String status(UUID requirementId) {
        return jdbc.queryForObject("select status from substitution_requirement where id = ?", String.class, requirementId);
    }

    protected int notifications(UUID user, String type) {
        return jdbc.queryForObject(
                "select count(*) from notification where recipient_user_id = ? and type = ?", Integer.class, user, type);
    }

    protected Resp assign(String token, UUID requirement, UUID substitute) {
        return post(
                SUBS + "/" + requirement + "/assign",
                token,
                Map.of("substituteTeacherId", substitute, "version", version(requirement)));
    }

    protected Resp release(String token, UUID requirement, String reason) {
        return post(SUBS + "/" + requirement + "/release", token, Map.of("reason", reason, "version", version(requirement)));
    }

    protected Resp cover(String token, UUID requirement) {
        return post(SUBS + "/" + requirement + "/cover", token, Map.of("note", "I will take the class", "version", version(requirement)));
    }

    protected Resp inform(String token, UUID requirement) {
        return post(
                SUBS + "/" + requirement + "/inform",
                token,
                Map.of("contact", "Principal", "method", "PHONE", "note", "Told by phone", "version", version(requirement)));
    }

    /** A second Teacher placed at the same School as the first of the World (for two absences on one day). */
    protected TeacherCtx colleagueOfA(World w) {
        return newTeacher(w.admin(), w.schoolA(), 60);
    }

    /** An active Teacher with no placement: free to cover. */
    protected TeacherCtx benchTeacher(World w) {
        return newTeacher(w.admin(), null, 0);
    }
}
