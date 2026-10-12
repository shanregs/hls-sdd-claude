package com.hls.payroll;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import com.hls.support.PayrollTestBase;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Spec 013a US4 (T039): the flag shows the people a designation leaves without a salary, by kind. */
class MissingSalaryReportTest extends PayrollTestBase {

    @SuppressWarnings("unchecked")
    private static Map<String, Object> missing(Map<String, Object> entry) {
        return (Map<String, Object>) entry.get("missingSalary");
    }

    private void giveManagerAnOwnSalary(String admin, UUID manager) {
        Resp resp = post(
                BASE + "/people/MANAGER/" + manager + "/salaries",
                admin,
                Map.of("amount", "45000", "effectiveOn", firstOfMonth().toString()));
        assertThat(resp.status()).as(resp.body()).isEqualTo(201);
    }

    private void giveTeacherAnOwnSalary(String admin, UUID teacher) {
        Resp resp = post(
                "/api/v1/teachers/" + teacher + "/salary",
                admin,
                Map.of("amount", new BigDecimal("20000"), "effectiveOn", LocalDate.now().minusDays(300).toString()));
        assertThat(resp.status()).as(resp.body()).isEqualTo(201);
    }

    @Test
    void aManagerDesignationIsFlaggedForManagersWithoutTheirOwnSalaryAndOnlyTheirOwnSalaryClearsIt() {
        String admin = signInAs(Role.ADMIN).token();
        UUID designation = designation(admin, "MANAGER");
        List<UUID> managers = new java.util.ArrayList<>();
        for (int i = 0; i < 2; i++) {
            ManagerCtx manager = newManager(admin);
            managers.add(manager.managerId());
            assertThat(designate(admin, manager.managerId(), designation, LocalDate.now().minusDays(100)).status())
                    .isEqualTo(200);
        }
        assertThat(missing(entryOf(admin, designation))).containsEntry("flag", true).containsEntry("people", 2);

        // the designation's amount is only a suggestion: it does not clear the gap
        recordSalaryOk(admin, designation, "40000", firstOfMonth());
        assertThat(missing(entryOf(admin, designation))).containsEntry("flag", true).containsEntry("people", 2);

        giveManagerAnOwnSalary(admin, managers.get(0));
        assertThat(missing(entryOf(admin, designation))).containsEntry("flag", true).containsEntry("people", 1);
        giveManagerAnOwnSalary(admin, managers.get(1));
        assertThat(missing(entryOf(admin, designation))).containsEntry("flag", false).containsEntry("people", 0);
    }

    @Test
    void aTeacherDesignationCountsTeachersWithNoSalaryOfTheirOwnNotTheDesignationsAmount() {
        String admin = signInAs(Role.ADMIN).token();
        UUID designation = designation(admin, "TEACHER");
        UUID withOwn = teacher(admin);
        UUID withoutA = teacher(admin);
        UUID withoutB = teacher(admin);
        for (UUID t : new UUID[] {withOwn, withoutA, withoutB}) {
            assertThat(teacherEmployment(admin, t, designation, null).status()).isEqualTo(200);
        }
        giveTeacherAnOwnSalary(admin, withOwn);

        assertThat(missing(entryOf(admin, designation))).containsEntry("flag", true).containsEntry("people", 2);

        // the designation's own amount is only a default: it does not clear the gap
        recordSalaryOk(admin, designation, "35000", firstOfMonth());
        assertThat(missing(entryOf(admin, designation))).containsEntry("flag", true).containsEntry("people", 2);

        giveTeacherAnOwnSalary(admin, withoutA);
        giveTeacherAnOwnSalary(admin, withoutB);
        assertThat(missing(entryOf(admin, designation))).containsEntry("flag", false).containsEntry("people", 0);
    }

    @Test
    void aDesignationNobodyHoldsIsNotFlagged() {
        String admin = signInAs(Role.ADMIN).token();
        UUID managers = designation(admin, "MANAGER");
        UUID teachers = designation(admin, "TEACHER");
        assertThat(missing(entryOf(admin, managers))).containsEntry("flag", false).containsEntry("people", 0);
        assertThat(missing(entryOf(admin, teachers))).containsEntry("flag", false).containsEntry("people", 0);
    }

    @Test
    void aRetiredDesignationThatIsStillHeldIsStillFlagged() {
        String admin = signInAs(Role.ADMIN).token();
        UUID designation = designation(admin, "MANAGER");
        ManagerCtx manager = newManager(admin);
        assertThat(designate(admin, manager.managerId(), designation, LocalDate.now().minusDays(100)).status())
                .isEqualTo(200);
        long version = ((Number) rows(get("/api/v1/designations?kind=MANAGER", admin)).stream()
                        .filter(r -> designation.toString().equals(r.get("id")))
                        .findFirst()
                        .orElseThrow()
                        .get("version"))
                .longValue();
        assertThat(put("/api/v1/designations/" + designation, admin, Map.of("retired", true, "version", version)).status())
                .isEqualTo(200);

        Map<String, Object> entry = entryOf(admin, designation);
        assertThat(entry).containsEntry("retired", true);
        assertThat(missing(entry)).containsEntry("flag", true).containsEntry("people", 1);
    }
}
