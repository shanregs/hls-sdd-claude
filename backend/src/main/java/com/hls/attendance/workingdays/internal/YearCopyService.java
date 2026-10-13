package com.hls.attendance.workingdays.internal;

import com.hls.attendance.api.WorkingCalendar;
import com.hls.attendance.workingdays.internal.WorkingDaysHistory.Action;
import com.hls.identity.user.Role;
import com.hls.school.api.ConflictException;
import com.hls.school.api.InvalidInputException;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Copies one year's figures of a School to another year (spec 037 FR-027). Months that already have a figure are never
 * touched, a figure that does not fit the target month (29 into February) is skipped and reported, and every row created
 * is audited and recorded in the history. The whole copy is one transaction.
 */
@Service
public class YearCopyService {

    public record Created(int month, BigDecimal workingDays) {}

    public record Skipped(int month, BigDecimal workingDays, String reason, Integer daysInMonth) {}

    public record Result(List<Created> created, List<Skipped> skipped) {}

    private final SchoolWorkingDaysRepository rows;
    private final WorkingDaysService service;
    private final WorkingDaysScope scope;
    private final WorkingCalendar calendar;
    private final TransactionTemplate tx;

    public YearCopyService(
            SchoolWorkingDaysRepository rows,
            WorkingDaysService service,
            WorkingDaysScope scope,
            WorkingCalendar calendar,
            PlatformTransactionManager transactionManager) {
        this.rows = rows;
        this.service = service;
        this.scope = scope;
        this.calendar = calendar;
        this.tx = new TransactionTemplate(transactionManager);
    }

    public Result copy(UUID actor, Set<Role> roles, UUID schoolId, Integer fromYear, Integer toYear) {
        if (schoolId == null || fromYear == null || toYear == null) {
            throw new InvalidInputException("School, from year and to year are required.");
        }
        if (fromYear.equals(toYear)) {
            throw new InvalidInputException("Choose a different year to copy to.");
        }
        scope.requireSchool(actor, roles, schoolId);
        service.requireSchoolExists(schoolId);
        WorkingDaysRules.checkNewYear(toYear, calendar.businessToday());
        try {
            return tx.execute(status -> copyInTransaction(actor, roles, schoolId, fromYear, toYear));
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("Someone else changed these figures at the same time. Try again.");
        }
    }

    private Result copyInTransaction(UUID actor, Set<Role> roles, UUID schoolId, int fromYear, int toYear) {
        List<SchoolWorkingDaysRow> source = rows.findBySchoolIdAndYear(schoolId, fromYear).stream()
                .sorted(java.util.Comparator.comparingInt(SchoolWorkingDaysRow::getMonth))
                .toList();
        if (source.isEmpty()) {
            throw new InvalidInputException("There are no figures for " + fromYear + " to copy.");
        }
        List<Created> created = new ArrayList<>();
        List<Skipped> skipped = new ArrayList<>();
        for (SchoolWorkingDaysRow row : source) {
            int month = row.getMonth();
            int days = YearMonth.of(toYear, month).lengthOfMonth();
            BigDecimal figure = row.getWorkingDays();
            if (rows.findBySchoolIdAndYearAndMonth(schoolId, toYear, month).isPresent()) {
                skipped.add(new Skipped(month, figure, "EXISTS", null));
            } else if (figure.compareTo(BigDecimal.valueOf(days)) > 0) {
                skipped.add(new Skipped(month, figure, "EXCEEDS_DAYS", days));
            } else {
                service.insert(actor, roles, schoolId, toYear, month, figure, Action.COPY);
                created.add(new Created(month, figure));
            }
        }
        return new Result(created, skipped);
    }
}
