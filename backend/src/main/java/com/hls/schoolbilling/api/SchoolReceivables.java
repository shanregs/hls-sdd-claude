package com.hls.schoolbilling.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * What Schools owe HLS, for payroll margin (013b), reports (014) and the dashboard (028). Read-only and bulk-oriented: the
 * number of queries does not depend on the number of ids. It checks no screen permission; callers apply their own scope. A
 * cancelled monthly due contributes nothing, and a month without a due has no entry (it is not zero).
 */
public interface SchoolReceivables {

    /** What one Teacher was billed for the month, across the Schools that billed them. */
    record TeacherBilled(UUID teacherId, YearMonth month, BigDecimal amount, List<UUID> schoolIds) {}

    /**
     * What one Teacher was billed at one School for the month, with the working days the due line bills at (a full month
     * has {@code workedDays == monthDays}). Spec 014b A1.
     */
    record TeacherSchoolBilled(
            UUID teacherId, UUID schoolId, YearMonth month, BigDecimal amount, BigDecimal workedDays, BigDecimal monthDays) {}

    /**
     * A School's live balances as of a date, by days past due. The five parts add up to {@code outstanding}; the bucket rule
     * is the one of the Ageing screen. Spec 014b A1.
     */
    record SchoolAgeing(
            UUID schoolId,
            BigDecimal outstanding,
            BigDecimal notYetDue,
            BigDecimal d0to30,
            BigDecimal d31to60,
            BigDecimal d61to90,
            BigDecimal over90) {}

    /** One School and month: owed (the due less its adjustments), collected, balance, and whether it is overdue. */
    record SchoolMonth(
            UUID schoolId, YearMonth month, BigDecimal owed, BigDecimal collected, BigDecimal balance, boolean overdue, LocalDate dueOn) {}

    /** A School's position over all months as of a date. */
    record SchoolBalance(
            UUID schoolId, BigDecimal owed, BigDecimal collected, BigDecimal outstanding, BigDecimal overdue, BigDecimal advanceCredit) {}

    /** Amount billed per Teacher per month in {@code from..to}, oldest month first. */
    Map<UUID, List<TeacherBilled>> billedPerTeacher(Collection<UUID> teacherIds, YearMonth from, YearMonth to);

    /** The School-month figures in {@code from..to}, oldest first. */
    Map<UUID, List<SchoolMonth>> schoolMonths(Collection<UUID> schoolIds, YearMonth from, YearMonth to);

    /** Every given School has an entry (zeros when it has never been billed). */
    Map<UUID, SchoolBalance> balances(Collection<UUID> schoolIds, LocalDate asOf);

    /**
     * Amount billed per Teacher and School and month in {@code from..to}, oldest month first. {@code schoolIds} is required
     * and an empty set gives an empty result (never "every School"); {@code teacherIds} null means every Teacher at those
     * Schools, and an empty set gives an empty result. Cancelled dues are dropped, as in {@link #billedPerTeacher}.
     */
    List<TeacherSchoolBilled> billedPerTeacherAndSchool(
            Collection<UUID> teacherIds, Collection<UUID> schoolIds, YearMonth from, YearMonth to);

    /** Live balances by days past due as of {@code asOf}; a School with nothing outstanding has no entry. */
    Map<UUID, SchoolAgeing> ageing(Collection<UUID> schoolIds, LocalDate asOf);
}
