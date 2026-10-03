package com.hls.attendance;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.attendance.internal.BusinessCalendar;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

/** Spec 008 research.md section 9: business dates follow Asia/Kolkata, not the UTC application clock. */
class BusinessCalendarTest {

    // 19:00 UTC on 3 Oct is 00:30 IST on 4 Oct.
    private final Clock lateUtc = Clock.fixed(Instant.parse("2026-10-03T19:00:00Z"), ZoneOffset.UTC);
    private final BusinessCalendar calendar = new BusinessCalendar(lateUtc, "Asia/Kolkata");

    @Test
    void todayIsTheIndianDateNotTheUtcDate() {
        assertThat(calendar.today()).isEqualTo(LocalDate.of(2026, 10, 4));
    }

    @Test
    void theSelfMarkWindowStartsThreeDaysBeforeTheIndianToday() {
        assertThat(calendar.windowStart()).isEqualTo(LocalDate.of(2026, 10, 1));
    }

    @Test
    void aMonthHasEndedOnlyOnceItsLastDayIsBeforeToday() {
        assertThat(calendar.monthHasEnded(YearMonth.of(2026, 9))).isTrue();
        assertThat(calendar.monthHasEnded(YearMonth.of(2026, 10))).isFalse();
        assertThat(calendar.monthHasEnded(YearMonth.of(2026, 11))).isFalse();
    }

    @Test
    void theLastDayOfAMonthDoesNotCountAsEndedUntilTheNextDayStarts() {
        Clock lastDay = Clock.fixed(Instant.parse("2026-09-30T12:00:00Z"), ZoneOffset.UTC);
        assertThat(new BusinessCalendar(lastDay, "Asia/Kolkata").monthHasEnded(YearMonth.of(2026, 9))).isFalse();
        Clock nextDayIst = Clock.fixed(Instant.parse("2026-09-30T19:00:00Z"), ZoneOffset.UTC);
        assertThat(new BusinessCalendar(nextDayIst, "Asia/Kolkata").monthHasEnded(YearMonth.of(2026, 9))).isTrue();
    }
}
