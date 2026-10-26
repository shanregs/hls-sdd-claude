package com.hls.attendance.manager.internal;

import com.hls.attendance.api.UnpaidDay;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * A Manager's month, worked out from their records (spec 032 research.md section 3). Pure: it reads no clock, no
 * repository and no calendar, so the same inputs always give the same figures. The caller passes the working dates
 * (the default calendar inside the Manager's employment) and today's date.
 *
 * <p>Per working date: a check-in is a worked day (a missing check-out changes nothing); approved leave is leave, paid
 * unless it is Loss-of-Pay; half-day leave leaves the other half to be worked or, with no check-in, unpaid; a date with
 * neither a check-in nor leave is unpaid. Amended by spec 024: with no check-in, an attended training day (whole or half)
 * counts as worked, up to the part of the day not on leave. Dates that are not over (today and later) are {@code pending}: leave on them
 * is counted as leave, but nothing on them is unpaid yet.
 */
public final class ManagerMonthCalculator {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(1);
    private static final BigDecimal ONE = BigDecimal.ONE.setScale(1);

    /**
     * What the records say about one date.
     *
     * @param checkedIn the Manager has a check-in that day
     * @param field the check-in was a Field day
     * @param leaveFraction 1.0 or 0.5 when approved leave covers the day, otherwise null
     * @param leaveUnpaid the leave is of the Loss-of-Pay type
     * @param training the training day attended (1.0 or 0.5, spec 024), counted as worked only where there is no check-in
     */
    public record DayRecord(boolean checkedIn, boolean field, BigDecimal leaveFraction, boolean leaveUnpaid, BigDecimal training) {

        public DayRecord(boolean checkedIn, boolean field, BigDecimal leaveFraction, boolean leaveUnpaid) {
            this(checkedIn, field, leaveFraction, leaveUnpaid, null);
        }
    }

    public record Figures(
            BigDecimal workingDays,
            BigDecimal daysWorked,
            BigDecimal leavePaid,
            BigDecimal leaveUnpaid,
            BigDecimal unpaid,
            BigDecimal fieldDays,
            List<UnpaidDay> unpaidDays,
            List<LocalDate> pending) {}

    private ManagerMonthCalculator() {}

    public static Figures compute(
            Collection<LocalDate> workingDates, Map<LocalDate, DayRecord> records, LocalDate today) {
        BigDecimal workingDays = ZERO;
        BigDecimal worked = ZERO;
        BigDecimal leavePaid = ZERO;
        BigDecimal leaveUnpaid = ZERO;
        BigDecimal unpaid = ZERO;
        BigDecimal fieldDays = ZERO;
        List<UnpaidDay> unpaidDays = new ArrayList<>();
        List<LocalDate> pending = new ArrayList<>();

        for (LocalDate date : new TreeMap<LocalDate, Boolean>(toMap(workingDates)).keySet()) {
            workingDays = workingDays.add(ONE);
            DayRecord record = records.get(date);
            boolean over = date.isBefore(today);
            if (!over) {
                pending.add(date);
            }
            BigDecimal leave = record == null || record.leaveFraction() == null
                    ? ZERO
                    : record.leaveFraction().setScale(1);
            BigDecimal rest = ONE.subtract(leave);
            BigDecimal unpaidThatDay = ZERO;

            if (leave.signum() > 0) {
                if (record.leaveUnpaid()) {
                    leaveUnpaid = leaveUnpaid.add(leave);
                    if (over) {
                        unpaidThatDay = unpaidThatDay.add(leave);
                    }
                } else {
                    leavePaid = leavePaid.add(leave);
                }
            }
            if (rest.signum() > 0) {
                if (record != null && record.checkedIn()) {
                    worked = worked.add(rest);
                    if (record.field()) {
                        fieldDays = fieldDays.add(rest);
                    }
                } else {
                    BigDecimal training = record == null || record.training() == null ? ZERO : record.training().setScale(1);
                    BigDecimal credited = training.min(rest);
                    worked = worked.add(credited);
                    if (over) {
                        unpaidThatDay = unpaidThatDay.add(rest.subtract(credited));
                    }
                }
            }
            if (unpaidThatDay.signum() > 0) {
                unpaid = unpaid.add(unpaidThatDay);
                unpaidDays.add(new UnpaidDay(date, unpaidThatDay));
            }
        }
        return new Figures(
                workingDays,
                worked,
                leavePaid,
                leaveUnpaid,
                unpaid,
                fieldDays,
                List.copyOf(unpaidDays),
                List.copyOf(pending));
    }

    private static Map<LocalDate, Boolean> toMap(Collection<LocalDate> dates) {
        Map<LocalDate, Boolean> map = new TreeMap<>();
        dates.forEach(d -> map.put(d, Boolean.TRUE));
        return map;
    }
}
