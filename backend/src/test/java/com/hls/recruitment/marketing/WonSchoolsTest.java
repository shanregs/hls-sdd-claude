package com.hls.recruitment.marketing;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.recruitment.api.WonSchools;
import com.hls.support.ExpensesTestBase;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Spec 015 T051: Schools won in a period, as business dates in Asia/Kolkata, optionally narrowed to a Zone. */
class WonSchoolsTest extends ExpensesTestBase {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    @Autowired
    private WonSchools won;

    private static Instant at(LocalDate date, int hour, int minute) {
        return date.atTime(hour, minute).atZone(IST).toInstant();
    }

    @Test
    void wonBetweenReadsTheBusinessDateInclusiveAndByZone() {
        UUID zone = zone(admin().token());
        UUID other = zone(admin().token());
        LocalDate day = LocalDate.of(2024, 6, 10);
        UUID late9 = insertWonProspect(zone, at(day.minusDays(1), 23, 30));
        UUID early10 = insertWonProspect(zone, at(day, 0, 30));
        UUID last = insertWonProspect(zone, at(day.plusDays(2), 23, 59));
        UUID after = insertWonProspect(zone, at(day.plusDays(3), 0, 1));
        insertWonProspect(other, noonOn(day));

        var inZone = won.wonBetween(day, day.plusDays(2), zone);
        assertThat(inZone).extracting(WonSchools.WonSchool::prospectId).containsExactlyInAnyOrder(early10, last);
        assertThat(inZone).extracting(WonSchools.WonSchool::wonOn).containsExactlyInAnyOrder(day, day.plusDays(2));
        assertThat(won.wonBetween(day, day.plusDays(2), null)).hasSizeGreaterThanOrEqualTo(3);
        assertThat(won.wonBetween(day.minusDays(1), day.minusDays(1), zone))
                .extracting(WonSchools.WonSchool::prospectId)
                .containsExactly(late9);
        assertThat(won.wonBetween(day.plusDays(3), day.plusDays(3), zone))
                .extracting(WonSchools.WonSchool::prospectId)
                .containsExactly(after);
        assertThat(won.wonBetween(day.plusDays(2), day, zone)).isEmpty();
    }
}
