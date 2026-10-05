package com.hls.recruitment;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import com.hls.teacher.api.TeacherRegistry;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Decision D13: the salary of the accepted offer is written once, on the Teacher's first assignment to a School. */
class FirstSalaryListenerTest extends RecruitmentTestBase {

    @Autowired
    TeacherRegistry registry;

    private record Hire(UUID teacher, UUID offer) {}

    private Hire hire(String adminToken, String directorToken, String salary) {
        UUID candidate = selected(adminToken, phone());
        UUID offer = issued(directorToken, candidate, salary);
        Resp accepted = post(BASE + "/offers/" + offer + "/accept", directorToken, Map.of());
        assertThat(accepted.status()).as(accepted.body()).isEqualTo(200);
        UUID teacher = UUID.fromString((String) accepted.map().get("teacherId"));
        // induction sign-off (spec 016 US4) makes the recruit active; here the registry stands in for it
        registry.activate(signInAs(Role.ADMIN).userId(), teacher);
        return new Hire(teacher, offer);
    }

    private List<BigDecimal> salaries(UUID teacher) {
        return jdbc.queryForList("select amount from teacher_salary_history where teacher_id = ? order by created_at", BigDecimal.class, teacher);
    }

    @Test
    void mappingATeacherFromAnAcceptedOfferWritesOneSalaryEntryEqualToTheOffer() {
        String admin = admin();
        String director = directorToken();
        Fixture a = fixture(admin);
        Fixture b = fixture(admin);
        UUID dir = signInAs(Role.DIRECTOR).userId();
        createContract(admin, a.schoolId(), startedDaysAgo(sameSalaryBody(a, dir, 2, "15000"), 20));
        createContract(admin, b.schoolId(), startedDaysAgo(sameSalaryBody(b, dir, 2, "15000"), 20));
        Hire hire = hire(admin, director, "18500");
        assertThat(salaries(hire.teacher())).isEmpty();

        assertThat(assign(admin, hire.teacher(), a.schoolId(), null, today.minusDays(4)).status()).isEqualTo(200);

        assertThat(salaries(hire.teacher())).hasSize(1);
        assertThat(salaries(hire.teacher()).get(0)).isEqualByComparingTo("18500");
        assertThat(jdbc.queryForObject("select effective_on from teacher_salary_history where teacher_id = ?", java.sql.Date.class, hire.teacher()).toLocalDate())
                .isEqualTo(today.minusDays(4));

        assertThat(assign(admin, hire.teacher(), b.schoolId(), null, today).status()).isEqualTo(200);
        assertThat(salaries(hire.teacher())).hasSize(1);
        assertChangeRecorded(admin, "TEACHER_FIRST_SALARY", hire.teacher(), "recorded");
    }

    @Test
    void aTrainedButUnplacedTeacherHasNoSalaryEntry() {
        Hire hire = hire(admin(), directorToken(), "17000");

        assertThat(salaries(hire.teacher())).isEmpty();
        assertThat(registry.hasSalaryEntry(hire.teacher())).isFalse();
    }

    @Test
    void aTeacherNotCreatedFromAnOfferIsUntouched() {
        String admin = admin();
        Fixture a = fixture(admin);
        UUID dir = signInAs(Role.DIRECTOR).userId();
        createContract(admin, a.schoolId(), startedDaysAgo(sameSalaryBody(a, dir, 2, "15000"), 20));
        UUID plain = teacher(admin);

        assertThat(assign(admin, plain, a.schoolId(), null, today.minusDays(2)).status()).isEqualTo(200);

        assertThat(salaries(plain)).isEmpty();
    }

    @Test
    void aTeacherWhoAlreadyHasASalaryEntryKeepsIt() {
        String admin = admin();
        String director = directorToken();
        Fixture a = fixture(admin);
        UUID dir = signInAs(Role.DIRECTOR).userId();
        createContract(admin, a.schoolId(), startedDaysAgo(sameSalaryBody(a, dir, 2, "15000"), 20));
        Hire hire = hire(admin, director, "18500");
        registry.recordFirstSalary(signInAs(Role.ADMIN).userId(), hire.teacher(), new BigDecimal("12000"), today.minusDays(10));

        assertThat(assign(admin, hire.teacher(), a.schoolId(), null, today.minusDays(2)).status()).isEqualTo(200);

        assertThat(salaries(hire.teacher())).hasSize(1);
        assertThat(salaries(hire.teacher()).get(0)).isEqualByComparingTo("12000");
    }
}
