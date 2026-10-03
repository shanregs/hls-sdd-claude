package com.hls.teacher;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import com.hls.support.MasterDataTestBase;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Spec 005 FR-005: a School with current or scheduled Teachers cannot be deactivated. */
class SchoolDeactivationGuardTest extends MasterDataTestBase {

    @Test
    void deactivationIsRefusedWhileATeacherIsPlacedAndAllowedOnceTheyMoveOrExit() {
        String admin = signInAs(Role.ADMIN).token();
        UUID school = schoolInNewZone(admin)[2];
        UUID elsewhere = schoolInNewZone(admin)[2];
        UUID teacher = teacher(admin);
        placeTeacher(admin, teacher, school);

        Resp refused = post("/api/v1/schools/" + school + "/deactivate", admin, null);
        assertThat(refused.status()).isEqualTo(409);
        assertThat(refused.body()).contains("still has Teachers");
        assertThat(get("/api/v1/schools/" + school, admin).map()).containsEntry("active", true);
        assertThat(get("/api/v1/schools/" + school, admin).map()).containsEntry("teacherCount", 1);

        placeTeacher(admin, teacher, elsewhere);
        assertThat(post("/api/v1/schools/" + school + "/deactivate", admin, null).status())
                .isEqualTo(204);
        post("/api/v1/teachers/" + teacher + "/status", admin, Map.of("status", "EXITED"));
        assertThat(post("/api/v1/schools/" + elsewhere + "/deactivate", admin, null).status())
                .isEqualTo(204);
    }

    @Test
    void aSchoolWithAScheduledIncomingTeacherCannotBeDeactivatedUntilTheMoveIsCancelled() {
        String admin = signInAs(Role.ADMIN).token();
        UUID current = schoolInNewZone(admin)[2];
        UUID incoming = schoolInNewZone(admin)[2];
        UUID teacher = teacher(admin);
        placeTeacherRaw(admin, teacher, current, LocalDate.now().minusDays(2));
        placeTeacherRaw(admin, teacher, incoming, LocalDate.now().plusDays(5));

        assertThat(post("/api/v1/schools/" + incoming + "/deactivate", admin, null).status())
                .isEqualTo(409);

        delete("/api/v1/teachers/" + teacher + "/placements/pending", admin);
        assertThat(post("/api/v1/schools/" + incoming + "/deactivate", admin, null).status())
                .isEqualTo(204);
    }
}
