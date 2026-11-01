package com.hls.substitution.payout;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hls.identity.user.Role;
import com.hls.support.SubstitutionTestBase;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Spec 017b US3 (T032, T035): the list, summary, one substitute's month and the CSV, per role and scope. */
class PayoutApiTest extends SubstitutionTestBase {

    private static final String PAYOUTS = "/api/v1/substitute-payouts";
    private static final ObjectMapper JSON = new ObjectMapper();

    /** A day for the School of Zone A covered by the first Substitute and one for Zone B covered by the second. */
    private record Setup(World w, TeacherCtx subA, TeacherCtx subB, UUID dayA, UUID dayB, YearMonth month) {}

    private Setup setup() {
        World w = newWorld();
        TeacherCtx subA = benchTeacher(w);
        TeacherCtx subB = benchTeacher(w);
        LocalDate day = monday();
        absent(w.managerA().token(), w.teacherA().teacherId(), day);
        absent(w.managerB().token(), w.teacherB().teacherId(), day);
        UUID reqA = requirementId(w.teacherA().teacherId(), day);
        UUID reqB = requirementId(w.teacherB().teacherId(), day);
        assertThat(assign(w.managerA().token(), reqA, subA.teacherId()).status()).isEqualTo(200);
        assertThat(assign(w.managerB().token(), reqB, subB.teacherId()).status()).isEqualTo(200);
        UUID dayA = jdbc.queryForObject("select id from substitute_day where requirement_id = ?", UUID.class, reqA);
        UUID dayB = jdbc.queryForObject("select id from substitute_day where requirement_id = ?", UUID.class, reqB);
        return new Setup(w, subA, subB, dayA, dayB, YearMonth.from(day));
    }

    private JsonNode json(Resp resp) throws Exception {
        assertThat(resp.status()).as(resp.body()).isEqualTo(200);
        return JSON.readTree(resp.body());
    }

    private static boolean contains(JsonNode content, UUID id) {
        for (JsonNode n : content) {
            if (id.toString().equals(n.get("id").asText())) {
                return true;
            }
        }
        return false;
    }

    @Test
    void theListIsScopedByZoneAndRefusesTeacherAndSystem() throws Exception {
        Setup s = setup();
        String list = PAYOUTS + "/days?month=" + s.month() + "&size=200";

        JsonNode a = json(get(list, s.w().managerA().token())).get("content");
        JsonNode b = json(get(list, s.w().managerB().token())).get("content");
        JsonNode admin = json(get(list, s.w().admin())).get("content");
        JsonNode director = json(get(list, s.w().director())).get("content");

        assertThat(contains(a, s.dayA())).isTrue();
        assertThat(contains(a, s.dayB())).as("Zone A does not see Zone B").isFalse();
        assertThat(contains(b, s.dayB())).isTrue();
        assertThat(contains(b, s.dayA())).isFalse();
        assertThat(contains(admin, s.dayA()) && contains(admin, s.dayB())).isTrue();
        assertThat(contains(director, s.dayA()) && contains(director, s.dayB())).isTrue();
        assertThat(get(list, s.w().teacherA().token()).status()).isEqualTo(403);
        assertThat(get(list, signInAs(Role.SYSTEM).token()).status()).isEqualTo(403);
    }

    @Test
    void theTotalsCoverTheWholeFilterNotOnlyThePage() throws Exception {
        Setup s = setup();

        JsonNode page = json(get(PAYOUTS + "/days?month=" + s.month() + "&size=1", s.w().admin()));

        assertThat(page.get("content")).hasSize(1);
        assertThat(page.get("totalElements").asLong()).isGreaterThanOrEqualTo(2);
        assertThat(page.get("totals").get("allocated").asLong()).isEqualTo(page.get("totalElements").asLong());
    }

    @Test
    void aSubstitutesMonthIsOpenToTheirZoneAndHiddenFromTheOther() throws Exception {
        Setup s = setup();
        String url = PAYOUTS + "/substitutes/" + s.subA().teacherId() + "?month=" + s.month();

        JsonNode month = json(get(url, s.w().managerA().token()));
        assertThat(month.get("days")).hasSize(1);
        assertThat(month.get("locked").asBoolean()).isFalse();

        assertThat(get(url, s.w().managerB().token()).status()).as("other Zone").isEqualTo(404);
        assertThat(get(url, s.w().admin()).status()).isEqualTo(200);
        assertThat(get(PAYOUTS + "/substitutes/" + UUID.randomUUID() + "?month=" + s.month(), s.w().admin()).status())
                .isEqualTo(404);
        assertThat(get(url, s.w().teacherA().token()).status()).isEqualTo(403);
    }

    @Test
    void theSummaryGroupsBySubstituteSchoolAndMonth() throws Exception {
        Setup s = setup();
        long version = jdbc.queryForObject("select version from substitute_day where id = ?", Long.class, s.dayA());
        assertThat(put(PAYOUTS + "/days/" + s.dayA() + "/attendance", s.w().managerA().token(),
                        Map.of("attendance", "PRESENT", "version", version))
                .status()).isEqualTo(200);

        JsonNode bySubstitute = json(get(PAYOUTS + "/summary?month=" + s.month() + "&groupBy=substitute", s.w().managerA().token()));
        JsonNode bySchool = json(get(PAYOUTS + "/summary?month=" + s.month() + "&groupBy=school", s.w().admin()));
        JsonNode byMonth = json(get(PAYOUTS + "/summary?month=" + s.month() + "&groupBy=month", s.w().admin()));

        assertThat(bySubstitute).as("Zone A sees one substitute").hasSize(1);
        assertThat(bySubstitute.get(0).get("attended").asLong()).isEqualTo(1);
        assertThat(bySubstitute.get(0).get("unpaid").asLong()).isEqualTo(1);
        assertThat(bySchool.size()).isGreaterThanOrEqualTo(2);
        assertThat(byMonth.get(0).get("key").asText()).isEqualTo(s.month().toString());
        assertThat(get(PAYOUTS + "/summary?groupBy=nonsense", s.w().admin()).status()).isEqualTo(400);
    }

    @Test
    void theCsvHasTheSameRowsAsTheListAndNeutralisesFormulasAndNeedsTheExportRight() throws Exception {
        Setup s = setup();
        jdbc.update("update teacher set name = '=HYPERLINK(\"x\")' where id = ?", s.subA().teacherId());
        String filter = "month=" + s.month();

        Resp csv = get(PAYOUTS + "/export?" + filter, s.w().admin());
        JsonNode list = json(get(PAYOUTS + "/days?" + filter + "&size=200", s.w().admin()));

        assertThat(csv.status()).isEqualTo(200);
        String[] lines = csv.body().replace("﻿", "").split("\r\n");
        assertThat(lines[0]).startsWith("Date,Substitute,School,Zone,Day,Attendance,Status,Rate,Amount");
        assertThat(lines.length - 1).as("one row per day in the list").isEqualTo(list.get("totalElements").asInt());
        assertThat(csv.body()).contains("'=HYPERLINK");
        assertThat(csv.body()).doesNotContain(",=HYPERLINK");
        assertThat(lines[1]).matches("\\d{2}/\\d{2}/\\d{4},.*");

        assertThat(get(PAYOUTS + "/export?" + filter, s.w().managerA().token()).status())
                .as("a Manager has no Export right")
                .isEqualTo(403);
        assertThat(get(PAYOUTS + "/export?" + filter, s.w().teacherA().token()).status()).isEqualTo(403);
        assertThat(jdbc.queryForObject(
                        "select count(*) from change_history_entry where entity_type = 'SUBSTITUTE_PAYOUT_EXPORT'",
                        Integer.class))
                .isGreaterThanOrEqualTo(1);
    }
}
