package com.hls.attendance.internal;

import com.hls.attendance.api.SchoolWorkingDays;
import com.hls.attendance.api.WorkingCalendar;
import com.hls.schoolbilling.api.BillingCalendar;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** The working days School billing needs (spec 022), served from the spec 037 month figure and the spec 008 calendar. */
@Component
class BillingCalendarImpl implements BillingCalendar {

    private final WorkingCalendar calendar;
    private final SchoolWorkingDays monthDays;

    BillingCalendarImpl(WorkingCalendar calendar, SchoolWorkingDays monthDays) {
        this.calendar = calendar;
        this.monthDays = monthDays;
    }

    @Override
    public BigDecimal monthWorkingDays(UUID schoolId, YearMonth month) {
        return monthDays.forMonth(schoolId, month).workingDays();
    }

    @Override
    public int workingDaysBetween(UUID schoolId, LocalDate from, LocalDate to) {
        return calendar.workingDays(schoolId, from, to).size();
    }
}
