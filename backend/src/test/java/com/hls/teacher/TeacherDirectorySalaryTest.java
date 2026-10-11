package com.hls.teacher;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import com.hls.support.DesignationTestBase;
import com.hls.teacher.api.TeacherDirectory;
import com.hls.teacher.api.TeacherDirectory.SalaryOn;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Spec 013a T033: the unchecked, server-side salary reads payroll uses; they are not reachable over HTTP. */
class TeacherDirectorySalaryTest extends DesignationTestBase {

    @Autowired
    private TeacherDirectory directory;

    private void salary(String admin, UUID teacher, String amount, LocalDate on) {
        Resp resp = post(
                "/api/v1/teachers/" + teacher + "/salary",
                admin,
                Map.of("amount", new BigDecimal(amount), "effectiveOn", on.toString()));
        assertThat(resp.status()).as(resp.body()).isEqualTo(201);
    }

    @Test
    void salaryOnIsTheLatestEntryOnOrBeforeTheDate() {
        String admin = signInAs(Role.ADMIN).token();
        UUID teacher = teacher(admin);
        LocalDate base = LocalDate.of(2026, 4, 1);
        salary(admin, teacher, "20000", base);
        salary(admin, teacher, "22000", base.plusMonths(3));

        assertThat(directory.salaryOn(teacher, base.minusDays(1))).isEmpty();
        assertThat(directory.salaryOn(teacher, base)).contains(new SalaryOn(new BigDecimal("20000.00"), base));
        assertThat(directory.salaryOn(teacher, base.plusMonths(3).minusDays(1)).orElseThrow().amount())
                .isEqualByComparingTo("20000");
        assertThat(directory.salaryOn(teacher, base.plusMonths(3)).orElseThrow().amount()).isEqualByComparingTo("22000");
    }

    @Test
    void twoEntriesOnOneDateGoToTheOneRecordedLast() {
        String admin = signInAs(Role.ADMIN).token();
        UUID teacher = teacher(admin);
        LocalDate day = LocalDate.of(2026, 4, 1);
        salary(admin, teacher, "20000", day);
        salary(admin, teacher, "21000", day);
        assertThat(directory.salaryOn(teacher, day).orElseThrow().amount()).isEqualByComparingTo("21000");
        assertThat(directory.salariesOn(List.of(teacher), day).get(teacher).amount()).isEqualByComparingTo("21000");
    }

    @Test
    void theBulkFormEqualsTheSingleFormAndLeavesOutTeachersWithNoSalary() {
        String admin = signInAs(Role.ADMIN).token();
        UUID withSalary = teacher(admin);
        UUID without = teacher(admin);
        LocalDate day = LocalDate.of(2026, 4, 1);
        salary(admin, withSalary, "25000", day.minusMonths(2));
        salary(admin, withSalary, "26000", day.plusMonths(1));

        Map<UUID, SalaryOn> bulk = directory.salariesOn(List.of(withSalary, without, UUID.randomUUID()), day);
        assertThat(bulk).containsOnlyKeys(withSalary);
        assertThat(bulk.get(withSalary)).isEqualTo(directory.salaryOn(withSalary, day).orElseThrow());
        assertThat(directory.salariesOn(List.of(), day)).isEmpty();
    }

    @Test
    void teachersByDesignationListsTheHolders() {
        String admin = signInAs(Role.ADMIN).token();
        UUID designation = designation(admin, "TEACHER");
        UUID one = teacher(admin);
        UUID two = teacher(admin);
        UUID other = teacher(admin);
        assertThat(teacherEmployment(admin, one, designation, null).status()).isEqualTo(200);
        assertThat(teacherEmployment(admin, two, designation, null).status()).isEqualTo(200);

        Map<UUID, List<UUID>> holders = directory.teachersByDesignation(List.of(designation));
        assertThat(holders.get(designation)).containsExactlyInAnyOrder(one, two).doesNotContain(other);
        assertThat(directory.teachersByDesignation(List.of(UUID.randomUUID()))).isEmpty();
    }

    @Test
    void thereIsNoHttpEndpointThatReturnsTheUncheckedRead() {
        String admin = signInAs(Role.ADMIN).token();
        UUID teacher = teacher(admin);
        // the profile endpoint never carries a salary member (spec 005 FR-019 still applies)
        assertThat(get("/api/v1/teachers/" + teacher, admin).body()).doesNotContain("salary");
    }
}
