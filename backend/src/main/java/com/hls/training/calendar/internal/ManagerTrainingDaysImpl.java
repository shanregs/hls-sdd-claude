package com.hls.training.calendar.internal;

import com.hls.attendance.api.ManagerTrainingDays;
import com.hls.organization.api.ManagerQueries;
import com.hls.organization.api.ManagerQueries.ManagerRef;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** What Manager attendance reads of training (spec 024 contracts/planned-activity-feed.md section 3). */
@Service("trainingManagerTrainingDays")
@Transactional(readOnly = true)
public class ManagerTrainingDaysImpl implements ManagerTrainingDays {

    private final ManagerQueries managers;
    private final AssignmentRepository assignments;
    private final TrainingSessionRepository sessions;
    private final AttendanceEntryRepository entries;

    ManagerTrainingDaysImpl(
            ManagerQueries managers, AssignmentRepository assignments, TrainingSessionRepository sessions, AttendanceEntryRepository entries) {
        this.managers = managers;
        this.assignments = assignments;
        this.sessions = sessions;
        this.entries = entries;
    }

    @Override
    public List<TrainingSessionRef> assignedSessionsOn(UUID managerUserId, LocalDate date) {
        UUID managerId = managers.managerByUserId(managerUserId).filter(ManagerRef::active).map(ManagerRef::id).orElse(null);
        if (managerId == null) {
            return List.of();
        }
        Set<UUID> assigned = assignments.findByPersonKindAndPersonId(Assignment.MANAGER, managerId).stream()
                .filter(Assignment::isRequired)
                .map(a -> a.sessionId)
                .collect(Collectors.toSet());
        if (assigned.isEmpty()) {
            return List.of();
        }
        return sessions.findByIdIn(assigned).stream()
                .filter(s -> date.equals(s.sessionDate))
                .filter(s -> TrainingSession.PUBLISHED.equals(s.status) || TrainingSession.HELD.equals(s.status))
                .map(s -> new TrainingSessionRef(s.id, s.sessionDate, "VIRTUAL".equals(s.venueKind) ? "Online" : s.venue, s.status))
                .toList();
    }

    @Override
    public Map<LocalDate, BigDecimal> attendedDays(UUID managerId, LocalDate from, LocalDate to) {
        List<AttendanceEntry> present = entries.findByPersonKindAndPersonIdAndOutcome(Assignment.MANAGER, managerId, AttendanceEntry.PRESENT);
        if (present.isEmpty()) {
            return Map.of();
        }
        Map<UUID, TrainingSession> byId = sessions.findByIdIn(present.stream().map(e -> e.sessionId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(s -> s.id, s -> s));
        Map<LocalDate, BigDecimal> out = new HashMap<>();
        for (AttendanceEntry e : present) {
            TrainingSession s = byId.get(e.sessionId);
            if (s == null || e.dayValue == null || s.sessionDate.isBefore(from) || s.sessionDate.isAfter(to)) {
                continue;
            }
            out.merge(s.sessionDate, e.dayValue, BigDecimal::max);
        }
        return out;
    }
}
