package com.hls.payroll;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import com.hls.support.PayrollTestBase;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Spec 013a US1 and US4: record a salary by designation, read it back, the history, and every refusal (T013, T040). */
class SalaryStructureApiTest extends PayrollTestBase {

    @Test
    void recordsASalaryAndReadsItBackWithTheEarlierRowUnchanged() {
        String admin = signInAs(Role.ADMIN).token();
        UUID designation = designation(admin, "MANAGER");
        LocalDate first = firstOfMonth();
        LocalDate later = first.plusMonths(3);

        Resp one = recordSalary(admin, designation, "30000", first, "Joining scale");
        assertThat(one.status()).as(one.body()).isEqualTo(201);
        assertThat(one.map()).containsEntry("amount", "30000.00").containsEntry("note", "Joining scale");
        Map<String, Object> firstRowBefore = historyOf(get(BASE + "/designations/" + designation, admin)).get(0);

        recordSalaryOk(admin, designation, "32000.50", later);

        Resp detail = get(BASE + "/designations/" + designation, admin);
        assertThat(detail.status()).isEqualTo(200);
        List<Map<String, Object>> history = historyOf(detail);
        assertThat(history).hasSize(2);
        assertThat(history.get(0)).containsEntry("amount", "32000.50").containsEntry("effectiveOn", later.toString());
        // the first row is exactly as it was before the second was recorded
        assertThat(history.get(1)).isEqualTo(firstRowBefore);
        // today falls in the first salary's period, not the later one
        assertThat(detail.map().get("inEffect")).isEqualTo(Map.of("amount", "30000.00", "effectiveOn", first.toString()));
    }

    @Test
    void twoRowsOnOneDateKeepBothAndTheOneRecordedLastIsInEffect() {
        String admin = signInAs(Role.ADMIN).token();
        UUID designation = designation(admin, "MANAGER");
        LocalDate first = firstOfMonth();
        recordSalaryOk(admin, designation, "20000", first);
        recordSalaryOk(admin, designation, "21000", first);

        Resp detail = get(BASE + "/designations/" + designation, admin);
        assertThat(historyOf(detail)).hasSize(2);
        assertThat(historyOf(detail).get(0)).containsEntry("amount", "21000.00");
        assertThat(detail.map().get("inEffect")).isEqualTo(Map.of("amount", "21000.00", "effectiveOn", first.toString()));
    }

    @Test
    void everyBadSalaryIsRefusedWithTheProblemNamed() {
        String admin = signInAs(Role.ADMIN).token();
        UUID designation = designation(admin, "MANAGER");
        LocalDate first = firstOfMonth();

        assertRefused(recordSalary(admin, designation, "0", first, null), "more than zero");
        assertRefused(recordSalary(admin, designation, "-1", first, null), "more than zero");
        assertRefused(recordSalary(admin, designation, "100.123", first, null), "two decimal places");
        assertRefused(recordSalary(admin, designation, "10000000", first, null), "9,999,999.99");
        assertRefused(recordSalary(admin, designation, null, first, null), "amount is required");
        assertRefused(recordSalary(admin, designation, "100", null, null), "effective date is required");
        assertRefused(recordSalary(admin, designation, "100", first.minusDays(1), null), "before the first day");
        assertRefused(recordSalary(admin, designation, "100", first, "n".repeat(501)), "500 characters");
        // nothing was stored by any refused request
        assertThat(historyOf(get(BASE + "/designations/" + designation, admin))).isEmpty();

        // the boundaries themselves are accepted
        assertThat(recordSalary(admin, designation, "9999999.99", first, "n".repeat(500)).status()).isEqualTo(201);
        assertThat(recordSalary(admin, designation, "0.01", first, null).status()).isEqualTo(201);
    }

    @Test
    void anUnknownDesignationIsNotFound() {
        String admin = signInAs(Role.ADMIN).token();
        UUID unknown = UUID.randomUUID();
        assertThat(recordSalary(admin, unknown, "100", firstOfMonth(), null).status()).isEqualTo(404);
        assertThat(get(BASE + "/designations/" + unknown, admin).status()).isEqualTo(404);
    }

    @Test
    void aRetiredDesignationStillTakesANewSalaryAndKeepsItsHistory() {
        String admin = signInAs(Role.ADMIN).token();
        UUID designation = designation(admin, "MANAGER");
        recordSalaryOk(admin, designation, "25000", firstOfMonth());
        long version = ((Number) rows(get("/api/v1/designations?kind=MANAGER", admin)).stream()
                        .filter(r -> designation.toString().equals(r.get("id")))
                        .findFirst()
                        .orElseThrow()
                        .get("version"))
                .longValue();
        assertThat(put("/api/v1/designations/" + designation, admin, Map.of("retired", true, "version", version)).status())
                .isEqualTo(200);

        Resp again = recordSalary(admin, designation, "26000", firstOfMonth().plusMonths(1), null);
        assertThat(again.status()).as(again.body()).isEqualTo(201);
        assertThat(historyOf(get(BASE + "/designations/" + designation, admin))).hasSize(2);
        // hidden from the default list, shown when retired designations are asked for
        assertThat(get(BASE + "/designations", admin).body()).doesNotContain(designation.toString());
        assertThat(entryOf(admin, designation)).containsEntry("retired", true);
    }

    @Test
    void theListSaysNoSalaryYetBeforeTheFirstEffectiveDate() {
        String admin = signInAs(Role.ADMIN).token();
        UUID designation = designation(admin, "MANAGER");
        assertThat(entryOf(admin, designation).get("inEffect")).isNull();

        recordSalaryOk(admin, designation, "40000", firstOfMonth().plusMonths(2));
        assertThat(entryOf(admin, designation).get("inEffect")).isNull();

        recordSalaryOk(admin, designation, "35000", firstOfMonth());
        assertThat(entryOf(admin, designation).get("inEffect"))
                .isEqualTo(Map.of("amount", "35000.00", "effectiveOn", firstOfMonth().toString()));
    }

    @Test
    void theListOfFiftyDesignationsLoadsFast() {
        String admin = signInAs(Role.ADMIN).token();
        for (int i = 0; i < 50; i++) {
            UUID designation = designation(admin, "MANAGER");
            if (i % 2 == 0) {
                recordSalaryOk(admin, designation, "10000", firstOfMonth());
            }
        }
        get(BASE + "/designations?kind=MANAGER", admin); // warm up
        long start = System.nanoTime();
        Resp list = get(BASE + "/designations?kind=MANAGER", admin);
        long millis = (System.nanoTime() - start) / 1_000_000;
        assertThat(list.status()).isEqualTo(200);
        assertThat(rows(list).size()).isGreaterThanOrEqualTo(50);
        assertThat(millis).as("designation list time in ms").isLessThan(1000);
    }

    @Test
    void theTeacherNoteIsOnTeacherDesignationsOnly() {
        String admin = signInAs(Role.ADMIN).token();
        UUID teacher = designation(admin, "TEACHER");
        UUID manager = designation(admin, "MANAGER");
        assertThat(entryOf(admin, teacher)).containsKey("teacherDefaultNote");
        assertThat(entryOf(admin, manager)).doesNotContainKey("teacherDefaultNote");
        assertThat(get(BASE + "/designations/" + teacher, admin).map()).containsKey("teacherDefaultNote");
        assertThat(get(BASE + "/designations/" + manager, admin).map()).doesNotContainKey("teacherDefaultNote");
    }

    @Test
    void theHistoryShowsEveryRowNewestFirstWithWhoWhenAndWhy() {
        Signed signed = signInAs(Role.ADMIN);
        UUID designation = designation(signed.token(), "MANAGER");
        LocalDate first = firstOfMonth();
        recordSalary(signed.token(), designation, "10000", first, "first");
        recordSalary(signed.token(), designation, "12000", first.plusMonths(1), "second");
        recordSalary(signed.token(), designation, "15000", first.plusMonths(2), "third");

        List<Map<String, Object>> history = historyOf(get(BASE + "/designations/" + designation, signed.token()));
        assertThat(history).extracting(r -> r.get("amount")).containsExactly("15000.00", "12000.00", "10000.00");
        assertThat(history).extracting(r -> r.get("note")).containsExactly("third", "second", "first");
        assertThat(history).allSatisfy(row -> {
            assertThat(row).containsKeys("effectiveOn", "recordedAt");
            assertThat(row.get("recordedBy")).isInstanceOf(Map.class);
            assertThat(((Map<?, ?>) row.get("recordedBy")).get("id")).isEqualTo(signed.userId().toString());
        });
        // three recorded salaries leave three audit entries an Admin can find in Change History
        assertChangeRecorded(signed.token(), "DESIGNATION_SALARY", designation, "amount");
    }

    private void assertRefused(Resp resp, String problem) {
        assertThat(resp.status()).as(resp.body()).isEqualTo(400);
        assertThat(resp.body()).contains(problem);
    }
}
