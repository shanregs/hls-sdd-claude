package com.hls.schoolbilling;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import com.hls.schoolbilling.api.SchoolContracts;
import com.hls.schoolbilling.api.SchoolReceivables;
import com.hls.schoolbilling.api.SchoolReceivables.SchoolAgeing;
import com.hls.schoolbilling.api.SchoolReceivables.TeacherSchoolBilled;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Spec 014b A1 and A2: the bulk reads the Margin, Collections and School reports use equal the 022 screens. */
class SchoolReceivablesReportReadsTest extends ReceivablesTestBase {

    @Autowired
    private SchoolReceivables receivables;

    @Autowired
    private SchoolContracts contracts;

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> list(Resp resp, String key) {
        return (List<Map<String, Object>>) resp.map().get(key);
    }

    @Test
    void feePerTeacherAndSchoolAddsUpToTheTeacherTotalAndKeepsTheBilledDays() {
        String admin = signInAs(Role.ADMIN).token();
        Fixture f = billedSchool(admin, "15000", "16000");
        UUID first = mapTeacher(admin, f, 0, thisMonth().atDay(1));
        UUID second = mapTeacher(admin, f, 1, thisMonth().atDay(1));
        raiseOne(admin, thisMonth(), f.schoolId());

        List<TeacherSchoolBilled> rows =
                receivables.billedPerTeacherAndSchool(Set.of(first, second), Set.of(f.schoolId()), thisMonth(), thisMonth());
        var total = receivables.billedPerTeacher(Set.of(first, second), thisMonth(), thisMonth());

        assertThat(rows).hasSize(2);
        for (TeacherSchoolBilled r : rows) {
            assertThat(r.schoolId()).isEqualTo(f.schoolId());
            assertThat(r.amount()).isEqualByComparingTo(total.get(r.teacherId()).get(0).amount());
            assertThat(r.workedDays()).isLessThanOrEqualTo(r.monthDays());
            assertThat(r.workedDays().signum()).isPositive();
        }
        assertThat(rows.stream().map(TeacherSchoolBilled::amount).reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo("31000.00");
    }

    @Test
    void anyTeacherAtTheSchoolsIsAllowedAsNullAndEmptyMeansNothing() {
        String admin = signInAs(Role.ADMIN).token();
        Fixture f = billedSchool(admin, "15000");
        UUID teacher = mapTeacher(admin, f, 0, thisMonth().atDay(1));
        raiseOne(admin, thisMonth(), f.schoolId());

        assertThat(receivables.billedPerTeacherAndSchool(null, Set.of(f.schoolId()), thisMonth(), thisMonth()))
                .extracting(TeacherSchoolBilled::teacherId).containsExactly(teacher);
        assertThat(receivables.billedPerTeacherAndSchool(null, Set.of(), thisMonth(), thisMonth())).isEmpty();
        assertThat(receivables.billedPerTeacherAndSchool(Set.of(), Set.of(f.schoolId()), thisMonth(), thisMonth())).isEmpty();
        assertThat(receivables.billedPerTeacherAndSchool(Set.of(teacher), Set.of(UUID.randomUUID()), thisMonth(), thisMonth()))
                .isEmpty();
    }

    @Test
    void aCancelledDueContributesNothingToTheFeePerSchool() {
        String admin = signInAs(Role.ADMIN).token();
        Fixture f = billedSchool(admin, "15000");
        UUID teacher = mapTeacher(admin, f, 0, thisMonth().atDay(1));
        UUID due = raiseOne(admin, thisMonth(), f.schoolId());
        assertThat(post(BILLING + "/dues/" + due + "/cancel", admin, Map.of("reason", "Wrong month")).status()).isEqualTo(200);

        assertThat(receivables.billedPerTeacherAndSchool(Set.of(teacher), Set.of(f.schoolId()), thisMonth(), thisMonth())).isEmpty();
    }

    @Test
    void ageingEqualsTheAgeingScreenAndTheBucketsAddUpToOutstanding() {
        String admin = signInAs(Role.ADMIN).token();
        Fixture f = billedSchoolSince(admin, 80, "10000");
        mapTeacher(admin, f, 0, thisMonth().minusMonths(1).atDay(1));
        UUID previous = raiseOne(admin, thisMonth().minusMonths(1), f.schoolId());
        UUID current = raiseOne(admin, thisMonth(), f.schoolId());
        makeOverdue(previous, 45);
        makeOverdue(current, -10);

        Map<UUID, SchoolAgeing> ageing = receivables.ageing(Set.of(f.schoolId()), java.time.LocalDate.now());
        Resp screen = get(BILLING + "/ageing", admin);
        Map<String, Object> row = list(screen, "schools").stream()
                .filter(m -> f.schoolId().toString().equals(m.get("schoolId"))).findFirst().orElseThrow();

        SchoolAgeing mine = ageing.get(f.schoolId());
        assertThat(mine.d31to60()).isEqualByComparingTo(dec(row.get("b31to60")));
        assertThat(mine.d0to30()).isEqualByComparingTo(dec(row.get("b0to30")));
        assertThat(mine.over90()).isEqualByComparingTo(dec(row.get("over90")));
        assertThat(mine.outstanding()).isEqualByComparingTo(dec(row.get("total")));
        assertThat(mine.notYetDue()).isEqualByComparingTo("10000.00");
        assertThat(mine.notYetDue().add(mine.d0to30()).add(mine.d31to60()).add(mine.d61to90()).add(mine.over90()))
                .isEqualByComparingTo(mine.outstanding());
        assertThat(receivables.ageing(Set.of(), java.time.LocalDate.now())).isEmpty();
    }

    @Test
    void aSchoolFullyPaidHasNoAgeingAndNeverANegativeOutstanding() {
        String admin = signInAs(Role.ADMIN).token();
        Fixture f = billedSchool(admin, "10000");
        mapTeacher(admin, f, 0, thisMonth().atDay(1));
        raiseOne(admin, thisMonth(), f.schoolId());
        assertThat(collect(admin, collectionBody(f.schoolId(), "12000")).status()).isEqualTo(201);

        assertThat(receivables.ageing(Set.of(f.schoolId()), java.time.LocalDate.now())).doesNotContainKey(f.schoolId());
    }

    @Test
    void contractsOfAllEqualsContractOfForEachSchoolAndSkipsOnesWithoutAContract() {
        String admin = signInAs(Role.ADMIN).token();
        Fixture a = billedSchool(admin, "15000");
        Fixture b = billedSchool(admin, "16000", "17000");
        Fixture none = fixture(admin);
        java.time.LocalDate today = java.time.LocalDate.now();

        Map<UUID, com.hls.schoolbilling.api.ContractView> all =
                contracts.contractsOfAll(Set.of(a.schoolId(), b.schoolId(), none.schoolId()), today);

        assertThat(all).containsOnlyKeys(a.schoolId(), b.schoolId());
        assertThat(all.get(a.schoolId())).isEqualTo(contracts.contractOf(a.schoolId(), today).orElseThrow());
        assertThat(all.get(b.schoolId())).isEqualTo(contracts.contractOf(b.schoolId(), today).orElseThrow());
        assertThat(contracts.contractsOfAll(Set.of(), today)).isEmpty();
    }
}
