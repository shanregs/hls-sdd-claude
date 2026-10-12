package com.hls.attendance.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/**
 * A Manager's month for payroll and reports (spec 032). Never a bare number: a month with no information says so, so
 * "no unpaid days" can only mean there really were none.
 */
public sealed interface ManagerMonth {

    /**
     * The month's figures. {@code workingDays} are the default-calendar working days inside the Manager's employment;
     * {@code daysWorked} counts a half day worked as 0.5; {@code leaveUnpaid} (Loss-of-Pay leave) is also part of
     * {@code unpaid}, which also holds days with no check-in and no leave. {@code pending} are the working dates not yet
     * over (today and later), never counted as unpaid.
     */
    record Found(
            YearMonth month,
            BigDecimal workingDays,
            BigDecimal daysWorked,
            BigDecimal leavePaid,
            BigDecimal leaveUnpaid,
            BigDecimal unpaid,
            BigDecimal fieldDays,
            List<UnpaidDay> unpaidDays,
            List<LocalDate> pending,
            boolean locked)
            implements ManagerMonth {}

    /** The Manager has no joining date (spec 005a), so no day can be called unpaid. */
    record NoJoiningDate() implements ManagerMonth {}

    /** No such Manager. */
    record Unknown() implements ManagerMonth {}
}
