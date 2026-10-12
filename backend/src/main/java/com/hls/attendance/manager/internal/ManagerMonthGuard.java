package com.hls.attendance.manager.internal;

import com.hls.school.api.ConflictException;
import java.time.YearMonth;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** The one check every writer shares: nothing changes in a Manager's locked month (spec 032 research.md section 6). */
@Component
public class ManagerMonthGuard {

    private final ManagerMonthStateRepository months;

    public ManagerMonthGuard(ManagerMonthStateRepository months) {
        this.months = months;
    }

    /** Refuses with a conflict that names the locked month. */
    public void requireOpen(UUID managerId, YearMonth month) {
        boolean locked = months.findById(new ManagerMonthState.Key(managerId, month))
                .map(ManagerMonthState::isLocked)
                .orElse(false);
        if (locked) {
            throw new ConflictException("The month " + month + " is locked for payroll, so this cannot be changed.");
        }
    }
}
