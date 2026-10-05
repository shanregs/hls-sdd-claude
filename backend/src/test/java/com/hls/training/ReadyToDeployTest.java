package com.hls.training;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReadyToDeployTest extends TrainingTestBase {

    @Test
    void aCompletedRecruitWithNoSchoolIsListedAndDropsOffOnceMapped() {
        String admin = admin();
        String director = directorToken();
        Fixture school = fixture(admin);
        UUID dir = signInAs(Role.DIRECTOR).userId();
        createContract(admin, school.schoolId(), startedDaysAgo(sameSalaryBody(school, dir, 2, "15000"), 20));
        UUID batch = runningBatch(director, 5);
        UUID teacher = recruit(admin, director);
        UUID enrolment = enrolmentOf(batch, teacher);

        assertThat(get(IND + "/ready-to-deploy", director).body()).doesNotContain(teacher.toString());
        post(IND + "/enrolments/" + enrolment + "/signoff", director, Map.of("result", "COMPLETED"));
        assertThat(get(IND + "/ready-to-deploy", director).body()).contains(teacher.toString());

        assertThat(assign(admin, teacher, school.schoolId(), null, today).status()).isEqualTo(200);

        assertThat(get(IND + "/ready-to-deploy", director).body()).doesNotContain(teacher.toString());
    }

    @Test
    void aRecruitStillInTrainingCannotBeMappedToASchool() {
        String admin = admin();
        String director = directorToken();
        Fixture school = fixture(admin);
        UUID dir = signInAs(Role.DIRECTOR).userId();
        createContract(admin, school.schoolId(), startedDaysAgo(sameSalaryBody(school, dir, 2, "15000"), 20));
        UUID teacher = recruit(admin, director);

        Resp refused = assign(admin, teacher, school.schoolId(), null, today);

        assertThat(refused.status()).isEqualTo(409);
        assertThat(refused.body()).contains("still in training");
    }

    @Test
    void aTeacherWhoWasNeverInductedIsNotReadyToDeploy() {
        String admin = admin();
        UUID plain = teacher(admin);

        assertThat(get(IND + "/ready-to-deploy", directorToken()).body()).doesNotContain(plain.toString());
    }
}
