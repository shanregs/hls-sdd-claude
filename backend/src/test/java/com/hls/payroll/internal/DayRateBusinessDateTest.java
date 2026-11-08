package com.hls.payroll.internal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hls.designation.api.BusinessDate;
import com.hls.designation.api.DesignationDirectory.Kind;
import com.hls.school.api.ChangeRecorder;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Regression: the day rate in effect today is looked up for the IST date. */
class DayRateBusinessDateTest {

    @Test
    void listLooksUpRatesForTheIstDate() {
        Clock clock = Clock.fixed(Instant.parse("2026-11-07T19:00:00Z"), ZoneOffset.UTC);
        DesignationDayRateRepository rates = mock(DesignationDayRateRepository.class);
        PersonDirectory people = mock(PersonDirectory.class);
        UUID designation = UUID.randomUUID();
        when(people.designations(Kind.TEACHER, false))
                .thenReturn(List.of(new PersonDirectory.DesignationRef(designation, "Teacher", Kind.TEACHER, false)));
        when(rates.findCandidates(any(), any())).thenReturn(List.of());

        DayRateService service =
                new DayRateService(rates, people, mock(ChangeRecorder.class), clock, new BusinessDate(clock, "Asia/Kolkata"));

        service.list(false);

        verify(rates).findCandidates(List.of(designation), LocalDate.parse("2026-11-08"));
    }
}
