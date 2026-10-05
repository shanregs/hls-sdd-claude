package com.hls.schoolbilling;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import com.hls.schoolbilling.api.Occupancy;
import com.hls.schoolbilling.api.SchoolContracts;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Spec 012 amendment A6: positions, filled and vacant of the contract in effect, for one School or many. */
class OccupancyTest extends SchoolContractsTestBase {

    @Autowired
    SchoolContracts contracts;

    @Test
    void positionsFilledAndVacantFollowTheMappedTeachers() {
        String admin = signInAs(Role.ADMIN).token();
        Fixture a = fixture(admin);
        Fixture b = fixture(admin);
        Fixture none = fixture(admin);
        UUID dir = director().userId();
        createContract(admin, a.schoolId(), startedDaysAgo(sameSalaryBody(a, dir, 3, "15000"), 20));
        createContract(admin, b.schoolId(), startedDaysAgo(perTeacherBody(b, dir, "15000", "16000"), 20));
        UUID teacher = teacher(admin);
        assertThat(assign(admin, teacher, a.schoolId(), null, today.minusDays(3)).status()).isEqualTo(200);

        assertThat(contracts.occupancyOf(a.schoolId(), today)).isEqualTo(new Occupancy(3, 1, 2));
        assertThat(contracts.occupancyOf(b.schoolId(), today)).isEqualTo(new Occupancy(2, 0, 2));
        assertThat(contracts.occupancyOf(none.schoolId(), today)).isEqualTo(Occupancy.NONE);

        Map<UUID, Occupancy> all = contracts.occupancyOfAll(List.of(a.schoolId(), b.schoolId(), none.schoolId()), today);
        assertThat(all).containsEntry(a.schoolId(), new Occupancy(3, 1, 2)).containsEntry(b.schoolId(), new Occupancy(2, 0, 2))
                .containsEntry(none.schoolId(), Occupancy.NONE);
        assertThat(contracts.occupancyOfAll(List.of(), today)).isEmpty();
    }

    @Test
    void aPendingContractAndADateBeforeTheStartHaveNoPositions() {
        String admin = signInAs(Role.ADMIN).token();
        Fixture f = fixture(admin);
        UUID dir = director().userId();
        createContract(admin, f.schoolId(), sameSalaryBody(f, dir, 2, "15000"));

        assertThat(contracts.occupancyOf(f.schoolId(), today.minusDays(5))).isEqualTo(Occupancy.NONE);
        assertThat(contracts.occupancyOf(f.schoolId(), today)).isEqualTo(new Occupancy(2, 0, 2));
    }
}
