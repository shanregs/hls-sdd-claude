package com.hls.transfer;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.schoolbilling.api.SchoolContracts;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Spec 027 T023 (SC-001): over a transfer date every day has exactly one School and the counts move by one. */
class TransferDayInvariantTest extends TransferTestBase {

    @Autowired
    private SchoolContracts contracts;

    private void checkAround(World w, UUID teacher, LocalDate date, UUID from, UUID to) {
        for (int offset = -3; offset <= 3; offset++) {
            LocalDate day = date.plusDays(offset);
            UUID school = schoolOn(teacher, day);
            if (day.isBefore(LocalDate.now())) {
                continue; // before the Teacher was placed
            }
            assertThat(school).as("School on " + day).isEqualTo(day.isBefore(date) ? from : to);
        }
    }

    private void runFor(int daysAhead) {
        World w = world();
        Fixture receiving = schoolWithMou(w, 2);
        UUID teacher = teacher(w.admin());
        Fixture leaving = schoolWithMou(w, 2);
        UUID leavingPosition = positionOf(leaving.schoolId(), 1);
        assertThat(assign(w.admin(), teacher, leaving.schoolId(), leavingPosition, null).status()).isEqualTo(200);
        LocalDate date = in(daysAhead);
        var beforeFrom = contracts.occupancyOf(leaving.schoolId(), date.minusDays(1));
        var beforeTo = contracts.occupancyOf(receiving.schoolId(), date.minusDays(1));
        UUID request = newTransfer(w.admin(), teacher, receiving.schoolId(), date);
        assertThat(approve(w.director(), request).status()).isEqualTo(200);
        checkAround(w, teacher, date, leaving.schoolId(), receiving.schoolId());
        assertThat(contracts.occupancyOf(leaving.schoolId(), date.minusDays(1)).filled()).isEqualTo(beforeFrom.filled());
        assertThat(date.minusDays(1)).isAfterOrEqualTo(LocalDate.now());
        assertThat(contracts.occupancyOf(leaving.schoolId(), date).filled()).isEqualTo(beforeFrom.filled() - 1);
        assertThat(contracts.occupancyOf(receiving.schoolId(), date.minusDays(1)).filled()).isEqualTo(beforeTo.filled());
        assertThat(contracts.occupancyOf(receiving.schoolId(), date).filled()).isEqualTo(beforeTo.filled() + 1);
    }

    @Test
    void tomorrow() {
        runFor(1);
    }

    @Test
    void monthEnd() {
        LocalDate now = LocalDate.now();
        runFor((int) java.time.temporal.ChronoUnit.DAYS.between(now, now.withDayOfMonth(now.lengthOfMonth()).plusDays(1)));
    }

    @Test
    void yearBoundary() {
        LocalDate now = LocalDate.now();
        LocalDate newYear = LocalDate.of(now.getYear() + 1, 1, 1);
        runFor((int) java.time.temporal.ChronoUnit.DAYS.between(now, newYear));
    }
}
