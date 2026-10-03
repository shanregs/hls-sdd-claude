package com.hls.teacher;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import com.hls.support.MasterDataTestBase;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Spec 005 FR-012: immediate, past, scheduled, cancelled and corrected placements; no overlap or gap. */
class TeacherPlacementServiceTest extends MasterDataTestBase {

    private final LocalDate today = LocalDate.now();

    private UUID schoolIn(String admin) {
        return schoolInNewZone(admin)[2];
    }

    private String detail(String admin, UUID teacher) {
        return get("/api/v1/teachers/" + teacher, admin).body();
    }

    @Test
    void placingTodayMakesTheSchoolCurrent() {
        String admin = signInAs(Role.ADMIN).token();
        UUID teacher = teacher(admin);
        UUID school = schoolIn(admin);

        Resp placed = placeTeacherRaw(admin, teacher, school, today);

        assertThat(placed.status()).isEqualTo(200);
        assertThat(placed.body()).contains("\"school\":{\"id\":\"" + school);
        assertChangeRecorded(admin, "TEACHER_PLACEMENT", teacher, "school");
    }

    @Test
    void movingEndsTheOldPlacementTheDayBeforeAndKeepsHistory() {
        String admin = signInAs(Role.ADMIN).token();
        UUID teacher = teacher(admin);
        UUID first = schoolIn(admin);
        UUID second = schoolIn(admin);
        placeTeacherRaw(admin, teacher, first, today.minusDays(10));

        Resp moved = placeTeacherRaw(admin, teacher, second, today.minusDays(2));

        assertThat(moved.status()).isEqualTo(200);
        String body = moved.body();
        assertThat(body).contains("\"school\":{\"id\":\"" + second);
        assertThat(body).contains("\"endsOn\":\"" + today.minusDays(3) + "\"");
        assertThat(body).contains(first.toString());
    }

    @Test
    void aDateBeforeTheCurrentPlacementStartedIsRefused() {
        String admin = signInAs(Role.ADMIN).token();
        UUID teacher = teacher(admin);
        UUID first = schoolIn(admin);
        UUID second = schoolIn(admin);
        placeTeacherRaw(admin, teacher, first, today.minusDays(5));

        Resp early = placeTeacherRaw(admin, teacher, second, today.minusDays(6));

        assertThat(early.status()).isEqualTo(409);
        assertThat(early.body()).contains("earlier than the start of the current placement");
        assertThat(detail(admin, teacher)).contains("\"school\":{\"id\":\"" + first);
    }

    @Test
    void aDateEqualToTheCurrentStartCorrectsTheOldRow() {
        String admin = signInAs(Role.ADMIN).token();
        UUID teacher = teacher(admin);
        UUID wrong = schoolIn(admin);
        UUID right = schoolIn(admin);
        placeTeacherRaw(admin, teacher, wrong, today.minusDays(4));

        Resp fixed = placeTeacherRaw(admin, teacher, right, today.minusDays(4));

        assertThat(fixed.status()).isEqualTo(200);
        assertThat(fixed.body()).contains("\"school\":{\"id\":\"" + right).contains("CORRECTED");
    }

    @Test
    void aFutureMoveIsScheduledAndNothingChangesToday() {
        String admin = signInAs(Role.ADMIN).token();
        UUID teacher = teacher(admin);
        UUID first = schoolIn(admin);
        UUID second = schoolIn(admin);
        placeTeacherRaw(admin, teacher, first, today.minusDays(3));

        Resp scheduled = placeTeacherRaw(admin, teacher, second, today.plusDays(7));

        assertThat(scheduled.status()).isEqualTo(200);
        assertThat(scheduled.body())
                .contains("\"school\":{\"id\":\"" + first)
                .contains("\"pendingPlacement\":{\"schoolId\":\"" + second)
                .contains("\"endsOn\":\"" + today.plusDays(6) + "\"");
    }

    @Test
    void cancellingAScheduledMoveRestoresTheOpenEnd() {
        String admin = signInAs(Role.ADMIN).token();
        UUID teacher = teacher(admin);
        UUID first = schoolIn(admin);
        UUID second = schoolIn(admin);
        placeTeacherRaw(admin, teacher, first, today.minusDays(3));
        placeTeacherRaw(admin, teacher, second, today.plusDays(7));

        assertThat(delete("/api/v1/teachers/" + teacher + "/placements/pending", admin).status())
                .isEqualTo(204);

        String body = detail(admin, teacher);
        assertThat(body).contains("\"pendingPlacement\":null").contains("CANCELLED");
        assertThat(body).doesNotContain("\"endsOn\":\"" + today.plusDays(6));
        assertThat(delete("/api/v1/teachers/" + teacher + "/placements/pending", admin).status())
                .isEqualTo(404);
    }

    @Test
    void aSecondScheduledMoveReplacesTheFirst() {
        String admin = signInAs(Role.ADMIN).token();
        UUID teacher = teacher(admin);
        UUID first = schoolIn(admin);
        UUID second = schoolIn(admin);
        UUID third = schoolIn(admin);
        placeTeacherRaw(admin, teacher, first, today.minusDays(3));
        placeTeacherRaw(admin, teacher, second, today.plusDays(5));

        Resp replaced = placeTeacherRaw(admin, teacher, third, today.plusDays(9));

        assertThat(replaced.status()).isEqualTo(200);
        assertThat(replaced.body()).contains("\"pendingPlacement\":{\"schoolId\":\"" + third);
        assertThat(replaced.body()).doesNotContain("\"pendingPlacement\":{\"schoolId\":\"" + second);
    }

    @Test
    void anExitedTeacherAnInactiveSchoolAndTheSameSchoolAreRefused() {
        String admin = signInAs(Role.ADMIN).token();
        UUID teacher = teacher(admin);
        UUID school = schoolIn(admin);
        UUID inactive = schoolIn(admin);
        post("/api/v1/schools/" + inactive + "/deactivate", admin, null);
        placeTeacher(admin, teacher, school);

        assertThat(placeTeacherRaw(admin, teacher, school, null).status()).isEqualTo(409);
        Resp inactiveResp = placeTeacherRaw(admin, teacher, inactive, null);
        assertThat(inactiveResp.status()).isEqualTo(409);
        assertThat(inactiveResp.body()).contains("inactive");

        post("/api/v1/teachers/" + teacher + "/status", admin, java.util.Map.of("status", "EXITED"));
        Resp exited = placeTeacherRaw(admin, teacher, schoolIn(admin), null);
        assertThat(exited.status()).isEqualTo(409);
        assertThat(exited.body()).contains("exited");
    }

    @Test
    void exitingEndsThePlacementAndCancelsAScheduledMove() {
        String admin = signInAs(Role.ADMIN).token();
        UUID teacher = teacher(admin);
        UUID first = schoolIn(admin);
        UUID second = schoolIn(admin);
        placeTeacherRaw(admin, teacher, first, today.minusDays(3));
        placeTeacherRaw(admin, teacher, second, today.plusDays(7));

        Resp exit = post("/api/v1/teachers/" + teacher + "/status", admin, java.util.Map.of("status", "EXITED"));

        assertThat(exit.status()).isEqualTo(200);
        assertThat(exit.body())
                .contains("\"school\":null")
                .contains("\"pendingPlacement\":null")
                .contains("\"endsOn\":\"" + today.minusDays(1) + "\"");
    }
}
