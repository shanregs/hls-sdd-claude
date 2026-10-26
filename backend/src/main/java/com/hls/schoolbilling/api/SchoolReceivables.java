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
}
