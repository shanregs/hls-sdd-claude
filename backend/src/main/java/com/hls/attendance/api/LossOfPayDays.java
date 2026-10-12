package com.hls.attendance.api;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The days attendance has flagged as loss of pay (amendment A2 to spec 008), for payroll and reports. The flag is set
 * when a mark is written, so payroll never repeats the rule of what makes a day unpaid.
 */
public interface LossOfPayDays {

    /** The Teacher's loss-of-pay days in the month, oldest first. */
    List<LossOfPayDay> lossOfPayDaysOf(UUID teacherId, YearMonth month);

    /** Bulk form for {@code from..to} inclusive; a Teacher with none is absent from the map. A fixed number of queries. */
    Map<UUID, List<LossOfPayDay>> lossOfPayDaysOf(Collection<UUID> teacherIds, LocalDate from, LocalDate to);
}
