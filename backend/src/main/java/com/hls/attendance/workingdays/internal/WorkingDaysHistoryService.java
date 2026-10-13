package com.hls.attendance.workingdays.internal;

import com.hls.attendance.workingdays.internal.WorkingDaysConflicts.RowDeleted;
import com.hls.attendance.workingdays.internal.WorkingDaysHistory.Action;
import com.hls.identity.user.Role;
import com.hls.school.api.InvalidInputException;
import com.hls.school.api.NotFoundException;
import com.hls.school.api.SchoolDirectory;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads the change history of a figure and the "Deleted rows" view (spec 037 FR-023). Both come from the feature's own
 * append-only history, keyed by School, year and month, so a deleted row's history is still found.
 */
@Service
public class WorkingDaysHistoryService {

    /** One change of a figure, newest first in a list. */
    public record Entry(
            String action, BigDecimal oldValue, BigDecimal newValue, String actorName, String actorRoles, Instant occurredAt) {}

    /** One deletion: the month, the last figure, who deleted it and when. */
    public record DeletedEntry(int month, BigDecimal lastValue, String deletedByName, Instant deletedAt) {}

    private final SchoolWorkingDaysRepository rows;
    private final WorkingDaysHistoryRepository history;
    private final SchoolDirectory schools;
    private final WorkingDaysScope scope;
    private final WorkingDaysViewFactory views;

    public WorkingDaysHistoryService(
            SchoolWorkingDaysRepository rows,
            WorkingDaysHistoryRepository history,
            SchoolDirectory schools,
            WorkingDaysScope scope,
            WorkingDaysViewFactory views) {
        this.rows = rows;
        this.history = history;
        this.schools = schools;
        this.scope = scope;
        this.views = views;
    }

    /** The history of an existing row; a row that no longer exists is {@link RowDeleted} (history stores no row id). */
    @Transactional(readOnly = true)
    public List<Entry> forRow(UUID userId, Set<Role> roles, UUID id) {
        SchoolWorkingDaysRow row = rows.findById(id).orElseThrow(RowDeleted::new);
        return forKey(userId, roles, row.getSchoolId(), row.getYear(), row.getMonth());
    }

    @Transactional(readOnly = true)
    public List<Entry> forKey(UUID userId, Set<Role> roles, UUID schoolId, int year, int month) {
        requireVisibleSchool(userId, roles, schoolId);
        if (month < 1 || month > 12) {
            throw new InvalidInputException("Month must be 1 to 12.");
        }
        List<WorkingDaysHistory> entries =
                history.findBySchoolIdAndYearAndMonthOrderByOccurredAtDesc(schoolId, year, month);
        Map<UUID, String> names = views.userNames(entries.stream().map(WorkingDaysHistory::getActorId).toList());
        return entries.stream()
                .map(h -> new Entry(
                        h.getAction().name(),
                        h.getOldValue(),
                        h.getNewValue(),
                        names.getOrDefault(h.getActorId(), "Unknown user"),
                        h.getActorRoles(),
                        h.getOccurredAt()))
                .toList();
    }

    /** Every deletion for the School and year, newest first, including months whose row was recreated since. */
    @Transactional(readOnly = true)
    public List<DeletedEntry> deleted(UUID userId, Set<Role> roles, UUID schoolId, int year) {
        requireVisibleSchool(userId, roles, schoolId);
        List<WorkingDaysHistory> entries =
                history.findBySchoolIdAndYearAndActionOrderByOccurredAtDesc(schoolId, year, Action.DELETE);
        Map<UUID, String> names = views.userNames(entries.stream().map(WorkingDaysHistory::getActorId).toList());
        return entries.stream()
                .map(h -> new DeletedEntry(
                        h.getMonth(),
                        h.getOldValue(),
                        names.getOrDefault(h.getActorId(), "Unknown user"),
                        h.getOccurredAt()))
                .toList();
    }

    private void requireVisibleSchool(UUID userId, Set<Role> roles, UUID schoolId) {
        scope.requireSchool(userId, roles, schoolId);
        if (schools.school(schoolId).isEmpty()) {
            throw new NotFoundException("School not found.");
        }
    }
}
