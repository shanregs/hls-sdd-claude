package com.hls.attendance.manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hls.attendance.manager.internal.CheckInService;
import com.hls.identity.user.Role;
import com.hls.school.api.InvalidInputException;
import com.hls.tasks.api.TaskCreation;
import com.hls.tasks.api.TaskCreation.FollowUpRequest;
import com.hls.tasks.api.TaskType;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Spec 032 US2: a Field day needs a purpose or a link to the Manager's own planned activity of that day. */
class FieldDayTest extends ManagerAttendanceTestBase {

    @Autowired
    TaskCreation taskCreation;

    private Map<String, Object> field(String purpose, List<Map<String, Object>> activities) {
        Map<String, Object> body = here("FIELD");
        if (purpose != null) {
            body.put("purpose", purpose);
        }
        if (activities != null) {
            body.put("activities", activities);
        }
        return body;
    }

    private Map<String, Object> link(String kind, UUID id) {
        return Map.of("kind", kind, "id", id.toString());
    }

    private UUID task(UUID assignee, LocalDate due) {
        var ref = taskCreation.createFollowUp(new FollowUpRequest(
                TaskType.FOLLOW_UP, "Collect the signed form", null, assignee, due, null, null, null, null,
                "Green Valley School", "TEST", "field-" + UUID.randomUUID(), null));
        assertThat(ref.created()).isTrue();
        return ref.id();
    }

    private UUID drive(String admin, UUID interviewerUserId, LocalDate date) {
        Resp college = post("/api/v1/recruitment/colleges", admin, Map.of("name", uniqueName("Field College"), "city", "Chennai"));
        assertThat(college.status()).as(college.body()).isEqualTo(201);
        Resp drive = post(
                "/api/v1/recruitment/drives",
                admin,
                Map.of("collegeId", college.id(), "dates", List.of(date.toString()), "interviewerUserIds", List.of(interviewerUserId)));
        assertThat(drive.status()).as(drive.body()).isEqualTo(201);
        return drive.id();
    }

    private UUID visit(String token, UUID zone, String admin, LocalDate date) {
        Map<String, Object> prospect = new HashMap<>();
        prospect.put("name", uniqueName("Prospect School"));
        prospect.put("board", "CBSE");
        prospect.put("address", "5 Lake Road");
        prospect.put("zoneId", zone);
        prospect.put("contactPerson", "Mrs Rao");
        prospect.put("designation", "Principal");
        prospect.put("phone", "9111111111");
        prospect.put("email", "rao@school.test");
        prospect.put("expectedTeachers", 4);
        Resp p = post("/api/v1/marketing/prospects", admin, prospect);
        assertThat(p.status()).as(p.body()).isEqualTo(201);
        UUID prospectId = UUID.fromString((String) ((Map<?, ?>) p.map().get("row")).get("id"));
        Resp a = post(
                "/api/v1/marketing/activities",
                token,
                Map.of("prospectId", prospectId, "type", "VISIT", "date", date.toString(), "notes", "Meet the principal"));
        assertThat(a.status()).as(a.body()).isEqualTo(201);
        return a.id();
    }

    private ManagerCtx manager(String admin) {
        ManagerCtx m = newManager(admin, zone(admin));
        jdbc.update("update manager set joining_date = ? where id = ?", today().minusDays(60), m.managerId());
        return m;
    }

    @Test
    void aFieldDayWithNeitherPurposeNorLinkIsRefusedWithTheReason() {
        ManagerCtx m = joinedManager(signInAs(Role.ADMIN).token());

        Resp none = post(ME + "/check-in", m.token(), field(null, null));
        Resp blank = post(ME + "/check-in", m.token(), field("   ", List.of()));

        assertThat(none.status()).isEqualTo(400);
        assertThat(none.body()).contains("purpose");
        assertThat(blank.status()).isEqualTo(400);
        assertThat(dayRows(m.managerId())).isZero();
    }

    @Test
    void aPurposeOf501CharactersIsRefusedAnd500IsAccepted() {
        ManagerCtx m = joinedManager(signInAs(Role.ADMIN).token());

        Resp tooLong = post(ME + "/check-in", m.token(), field("x".repeat(501), null));
        assertThat(tooLong.status()).isEqualTo(400);
        assertThat(tooLong.body()).contains("500");
        assertThat(dayRows(m.managerId())).isZero();

        Resp ok = post(ME + "/check-in", m.token(), field("x".repeat(500), null));
        assertThat(ok.status()).as(ok.body()).isEqualTo(201);
    }

    @Test
    void aPurposeOnlyFieldDayIsRecordedAndFlaggedUnlinked() {
        ManagerCtx m = joinedManager(signInAs(Role.ADMIN).token());

        Resp resp = post(ME + "/check-in", m.token(), field("  Visit to St Mary School  ", null));

        assertThat(resp.status()).as(resp.body()).isEqualTo(201);
        assertThat(resp.map()).containsEntry("dayType", "FIELD").containsEntry("purpose", "Visit to St Mary School");
        assertThat(flags(resp)).contains("UNLINKED");
        Map<String, Object> row = jdbc.queryForMap(
                "select purpose, unlinked from manager_day where manager_id = ?", m.managerId());
        assertThat(row).containsEntry("purpose", "Visit to St Mary School").containsEntry("unlinked", true);
        assertThat(count(m.managerId())).isZero();
    }

    @Test
    void aFieldDayLinkingATaskADriveAndAVisitOfTheManagerIsRecordedAndNotFlagged() {
        String admin = signInAs(Role.ADMIN).token();
        UUID zone = zone(admin);
        ManagerCtx m = newManager(admin, zone);
        jdbc.update("update manager set joining_date = ? where id = ?", today().minusDays(60), m.managerId());
        UUID userId = m.signed().userId();
        UUID task = task(userId, today());
        UUID drive = drive(admin, userId, today());
        UUID visit = visit(m.token(), zone, admin, today());

        Resp resp = post(
                ME + "/check-in",
                m.token(),
                field(null, List.of(link("FOLLOW_UP", task), link("CAMPUS_DRIVE", drive), link("SCHOOL_VISIT", visit))));

        assertThat(resp.status()).as(resp.body()).isEqualTo(201);
        assertThat(flags(resp)).doesNotContain("UNLINKED");
        assertThat(count(m.managerId())).isEqualTo(3);
        assertThat(jdbc.queryForObject("select unlinked from manager_day where manager_id = ?", Boolean.class, m.managerId()))
                .isFalse();
    }

    @Test
    void aPurposeWithALinkIsNotFlagged() {
        String admin = signInAs(Role.ADMIN).token();
        ManagerCtx m = manager(admin);
        UUID task = task(m.signed().userId(), today());

        Resp resp = post(ME + "/check-in", m.token(), field("Follow up", List.of(link("FOLLOW_UP", task))));

        assertThat(resp.status()).as(resp.body()).isEqualTo(201);
        assertThat(flags(resp)).doesNotContain("UNLINKED");
        assertThat(resp.map()).containsEntry("purpose", "Follow up");
    }

    @Test
    void aLinkToAnotherUsersAnotherDatesOrAnUnknownActivityIsRefused() {
        String admin = signInAs(Role.ADMIN).token();
        ManagerCtx m = manager(admin);
        ManagerCtx other = manager(admin);
        UUID othersTask = task(other.signed().userId(), today());
        UUID tomorrowsTask = task(m.signed().userId(), today().plusDays(1));

        for (Map<String, Object> bad : List.of(
                link("FOLLOW_UP", othersTask),
                link("FOLLOW_UP", tomorrowsTask),
                link("FOLLOW_UP", UUID.randomUUID()),
                link("NO_SUCH_KIND", UUID.randomUUID()))) {
            Resp resp = post(ME + "/check-in", m.token(), field("Visit", List.of(bad)));
            assertThat(resp.status()).as(resp.body()).isEqualTo(404);
        }
        assertThat(dayRows(m.managerId())).isZero();
    }

    @Test
    void anOfficeCheckInIgnoresAPurposeAndLinks() {
        String admin = signInAs(Role.ADMIN).token();
        ManagerCtx m = manager(admin);
        Map<String, Object> body = here("OFFICE");
        body.put("purpose", "Should be dropped");
        body.put("activities", List.of(link("FOLLOW_UP", UUID.randomUUID())));

        Resp resp = post(ME + "/check-in", m.token(), body);

        assertThat(resp.status()).as(resp.body()).isEqualTo(201);
        assertThat(resp.map().get("purpose")).isNull();
        assertThat(flags(resp)).doesNotContain("UNLINKED");
        assertThat(count(m.managerId())).isZero();
    }

    @Test
    void todayListsTheManagersPlannedActivitiesOfTheDay() {
        String admin = signInAs(Role.ADMIN).token();
        UUID zone = zone(admin);
        ManagerCtx m = newManager(admin, zone);
        jdbc.update("update manager set joining_date = ? where id = ?", today().minusDays(60), m.managerId());
        ManagerCtx other = manager(admin);
        UUID userId = m.signed().userId();
        UUID task = task(userId, today());
        UUID drive = drive(admin, userId, today());
        UUID visit = visit(m.token(), zone, admin, today());
        UUID tomorrow = task(userId, today().plusDays(1));
        UUID others = task(other.signed().userId(), today());

        Resp resp = get(ME + "/today", m.token());

        assertThat(resp.status()).as(resp.body()).isEqualTo(200);
        assertThat(resp.body())
                .contains(task.toString(), drive.toString(), visit.toString())
                .doesNotContain(tomorrow.toString(), others.toString());
        assertThat(resp.body()).contains("CAMPUS_DRIVE", "SCHOOL_VISIT", "FOLLOW_UP");
    }

    @Test
    void theFieldRuleCanBeReusedByACorrectionAndRefusesNeitherPurposeNorLink() {
        assertThatThrownBy(() -> CheckInService.requireFieldBasis(null, 0)).isInstanceOf(InvalidInputException.class);
        assertThatThrownBy(() -> CheckInService.requireFieldBasis("  ", 0)).isInstanceOf(InvalidInputException.class);
        assertThat(CheckInService.requireFieldBasis(" Visit ", 0)).isEqualTo("Visit");
        assertThat(CheckInService.requireFieldBasis(null, 1)).isNull();
    }

    @SuppressWarnings("unchecked")
    private static List<Object> flags(Resp resp) {
        return (List<Object>) resp.map().get("flags");
    }

    private long count(UUID managerId) {
        return jdbc.queryForObject(
                "select count(*) from manager_day_activity a join manager_day d on d.id = a.day_id where d.manager_id = ?",
                Long.class,
                managerId);
    }
}
