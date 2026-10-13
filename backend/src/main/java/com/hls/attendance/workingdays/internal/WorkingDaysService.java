package com.hls.attendance.workingdays.internal;

import com.hls.attendance.api.WorkingCalendar;
import com.hls.attendance.workingdays.internal.WorkingDaysConflicts.Duplicate;
import com.hls.attendance.workingdays.internal.WorkingDaysConflicts.RowDeleted;
import com.hls.attendance.workingdays.internal.WorkingDaysConflicts.Stale;
import com.hls.attendance.workingdays.internal.WorkingDaysHistory.Action;
import com.hls.identity.user.Role;
import com.hls.school.api.ChangeRecorder;
import com.hls.school.api.InvalidInputException;
import com.hls.school.api.SchoolDirectory;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Create, edit and delete of a School's monthly working days figure (spec 037 FR-001 to FR-004, FR-021, FR-022). Each
 * write is one transaction that also appends the history row and publishes the audit event; a refused change writes
 * nothing. Two people at once are told through a version check, and a row deleted meanwhile is never recreated.
 */
@Service
public class WorkingDaysService {

    /** The entity type of the central audit entries. */
    public static final String AUDIT_TYPE = "SCHOOL_WORKING_DAYS";

    private final SchoolWorkingDaysRepository rows;
    private final WorkingDaysHistoryRepository history;
    private final SchoolDirectory schools;
    private final WorkingCalendar calendar;
    private final ChangeRecorder changes;
    private final WorkingDaysScope scope;
    private final Clock clock;
    private final TransactionTemplate tx;

    public WorkingDaysService(
            SchoolWorkingDaysRepository rows,
            WorkingDaysHistoryRepository history,
            SchoolDirectory schools,
            WorkingCalendar calendar,
            ChangeRecorder changes,
            WorkingDaysScope scope,
            Clock clock,
            PlatformTransactionManager transactionManager) {
        this.rows = rows;
        this.history = history;
        this.schools = schools;
        this.calendar = calendar;
        this.changes = changes;
        this.scope = scope;
        this.clock = clock;
        this.tx = new TransactionTemplate(transactionManager);
    }

    public SchoolWorkingDaysRow create(
            UUID actor, Set<Role> roles, UUID schoolId, Integer year, Integer month, BigDecimal workingDays) {
        if (schoolId == null || year == null || month == null) {
            throw new InvalidInputException("School, year and month are required.");
        }
        scope.requireSchool(actor, roles, schoolId);
        requireSchoolExists(schoolId);
        WorkingDaysRules.checkNewYear(year, calendar.businessToday());
        BigDecimal figure = WorkingDaysRules.checkFigure(workingDays, year, month);
        try {
            return tx.execute(status -> insert(actor, roles, schoolId, year, month, figure, Action.CREATE));
        } catch (DataIntegrityViolationException e) {
            // two creates at the same moment: the unique key let one in; tell the other about it
            throw rows.findBySchoolIdAndYearAndMonth(schoolId, year, month)
                    .<RuntimeException>map(Duplicate::new)
                    .orElse(e);
        }
    }

    public SchoolWorkingDaysRow edit(UUID actor, Set<Role> roles, UUID id, BigDecimal workingDays, Long version) {
        requireVersion(version);
        try {
            return tx.execute(status -> {
                SchoolWorkingDaysRow row = rows.findById(id).orElseThrow(RowDeleted::new);
                scope.requireSchool(actor, roles, row.getSchoolId());
                checkVersion(row, version);
                BigDecimal figure = WorkingDaysRules.checkFigure(workingDays, row.getYear(), row.getMonth());
                BigDecimal old = row.getWorkingDays();
                if (figure.compareTo(old) == 0) {
                    return row;
                }
                row.change(figure, actor, clock.instant());
                rows.saveAndFlush(row);
                record(actor, roles, row, Action.EDIT, old, figure);
                changes.record(actor, AUDIT_TYPE, row.getId(), "workingDays", old, figure);
                return row;
            });
        } catch (ObjectOptimisticLockingFailureException e) {
            throw conflictFor(id);
        }
    }

    public void delete(UUID actor, Set<Role> roles, UUID id, Long version) {
        requireVersion(version);
        try {
            tx.executeWithoutResult(status -> {
                SchoolWorkingDaysRow row = rows.findById(id).orElseThrow(RowDeleted::new);
                scope.requireSchool(actor, roles, row.getSchoolId());
                checkVersion(row, version);
                rows.delete(row);
                rows.flush();
                record(actor, roles, row, Action.DELETE, row.getWorkingDays(), null);
                changes.recordLifecycle(actor, AUDIT_TYPE, row.getId(), "deleted", describe(row, row.getWorkingDays()));
            });
        } catch (ObjectOptimisticLockingFailureException e) {
            throw conflictFor(id);
        }
    }

    /** Adds one row in the caller's transaction, refusing a second figure for the same School and month. */
    SchoolWorkingDaysRow insert(
            UUID actor, Set<Role> roles, UUID schoolId, int year, int month, BigDecimal figure, Action action) {
        var existing = rows.findBySchoolIdAndYearAndMonth(schoolId, year, month);
        if (existing.isPresent()) {
            throw new Duplicate(existing.get());
        }
        SchoolWorkingDaysRow row = rows.saveAndFlush(new SchoolWorkingDaysRow(schoolId, year, month, figure, actor, clock.instant()));
        record(actor, roles, row, action, null, figure);
        String detail = describe(row, figure) + (action == Action.COPY ? " (copied from another year)" : "");
        changes.recordLifecycle(actor, AUDIT_TYPE, row.getId(), "created", detail);
        return row;
    }

    void requireSchoolExists(UUID schoolId) {
        if (schools.school(schoolId).isEmpty()) {
            throw new InvalidInputException("School not found.");
        }
    }

    private void record(
            UUID actor, Set<Role> roles, SchoolWorkingDaysRow row, Action action, BigDecimal old, BigDecimal now) {
        history.save(new WorkingDaysHistory(
                row.getSchoolId(),
                row.getYear(),
                row.getMonth(),
                action,
                old,
                now,
                actor,
                roleNames(roles),
                clock.instant()));
    }

    private static String roleNames(Set<Role> roles) {
        String joined = roles.stream().map(Enum::name).sorted().collect(Collectors.joining(","));
        return joined.length() > 100 ? joined.substring(0, 100) : joined;
    }

    private static String describe(SchoolWorkingDaysRow row, BigDecimal figure) {
        return "school " + row.getSchoolId() + ", " + row.getYear() + "-" + String.format("%02d", row.getMonth()) + ": " + figure;
    }

    private static void requireVersion(Long version) {
        if (version == null) {
            throw new InvalidInputException("The record version is required.");
        }
    }

    private static void checkVersion(SchoolWorkingDaysRow row, Long version) {
        if (!version.equals(row.getVersion())) {
            throw new Stale(row);
        }
    }

    /** What the second of two simultaneous saves is told: the row is gone, or it changed. */
    private RuntimeException conflictFor(UUID id) {
        return rows.findById(id).<RuntimeException>map(Stale::new).orElseGet(RowDeleted::new);
    }
}
