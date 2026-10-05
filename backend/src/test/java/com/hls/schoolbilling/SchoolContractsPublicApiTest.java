package com.hls.schoolbilling;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import com.hls.schoolbilling.api.ContractView;
import com.hls.schoolbilling.api.SchoolContracts;
import com.hls.schoolbilling.api.TeacherPosition;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Spec 012 FR-017 and SC-008: the public interface specs 013 and 022 read. It answers what the screens show:
 * the contract in effect, the position a Teacher fills and its salary, and who is not mapped.
 */
class SchoolContractsPublicApiTest extends SchoolContractsTestBase {

    @Autowired
    private SchoolContracts contracts;

    @Test
    void contractPositionAndSalaryAreReadThroughThePublicInterface() {
        String admin = signInAs(Role.ADMIN).token();
        Fixture f = fixture(admin);
        UUID contract = contractId(createContract(admin, f.schoolId(), perTeacherBody(f, director().userId(), "15000", "18000")));
        UUID teacher = teacher(admin);
        UUID second = UUID.fromString((String) positionsOf(contractsOf(admin, f.schoolId()).get(0)).get(1).get("id"));
        assertThat(assign(admin, teacher, f.schoolId(), second, today).status()).isEqualTo(200);

        Optional<ContractView> view = contracts.contractOf(f.schoolId(), today);
        assertThat(view).isPresent();
        assertThat(view.get().id()).isEqualTo(contract);
        assertThat(view.get().state()).isEqualTo("ACTIVE");
        assertThat(view.get().salaryMode()).isEqualTo("PER_TEACHER");
        assertThat(view.get().positions()).extracting(p -> p.salary()).containsExactly(new BigDecimal("15000.00"), new BigDecimal("18000.00"));

        Optional<TeacherPosition> position = contracts.positionOf(teacher, today);
        assertThat(position).isPresent();
        assertThat(position.get().positionId()).isEqualTo(second);
        assertThat(position.get().number()).isEqualTo(2);
        assertThat(position.get().salary()).isEqualByComparingTo("18000.00");
        assertThat(position.get().schoolId()).isEqualTo(f.schoolId());
        assertThat(contracts.positionOf(teacher, today.minusDays(40))).isEmpty();
        assertThat(contracts.contractsOverlapping(f.schoolId(), today.minusDays(5), today.plusDays(5))).hasSize(1);
        assertThat(contracts.contractOf(f.schoolId(), today.minusDays(3))).isEmpty();
    }

    @Test
    void aTeacherWithoutAPositionIsReportedAsNotMapped() {
        String admin = signInAs(Role.ADMIN).token();
        Fixture f = fixture(admin);
        UUID carriedOver = teacher(admin);
        assertThat(assign(admin, carriedOver, f.schoolId(), null, today).status()).isEqualTo(200);

        assertThat(contracts.unmappedTeachers(f.schoolId(), today, today)).containsExactly(carriedOver);
        assertThat(contracts.positionOf(carriedOver, today)).isEmpty();
        assertThat(contracts.contractOf(f.schoolId(), today).get().state()).isEqualTo("RATE_PENDING");

        // once the MoU is recorded and the Teacher mapped, the Teacher is no longer unmapped
        String pendingId = (String) contractsOf(admin, f.schoolId()).get(0).get("id");
        Map<String, Object> mou = sameSalaryBody(f, director().userId(), 2, "12000");
        mou.remove("startsOn");
        assertThat(put(BASE + "/contracts/" + pendingId + "/mou", admin, mou).status()).isEqualTo(200);
        UUID position = UUID.fromString((String) positionsOf(contractsOf(admin, f.schoolId()).get(0)).get(0).get("id"));
        assertThat(post(BASE + "/contracts/" + pendingId + "/map-teachers", admin,
                        List.of(Map.of("teacherId", carriedOver, "positionId", position)))
                        .status())
                .isEqualTo(200);
        assertThat(contracts.unmappedTeachers(f.schoolId(), today, today)).isEqualTo(Set.of());
        assertThat(contracts.positionOf(carriedOver, today)).isPresent();
    }
}
