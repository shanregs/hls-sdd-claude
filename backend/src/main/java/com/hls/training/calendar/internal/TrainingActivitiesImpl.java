package com.hls.training.calendar.internal;

import com.hls.training.api.TrainingActivities;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The planned-activity feed of training sessions (spec 024 contracts/planned-activity-feed.md). A fixed number of queries
 * (sessions, assignments, Managers, date history) however many sessions fall in the range.
 */
@Service("trainingActivitiesImpl")
@Transactional(readOnly = true)
public class TrainingActivitiesImpl implements TrainingActivities {

    private final TrainingSessionRepository sessions;
    private final AssignmentRepository assignments;
    private final SessionHistoryRepository history;
    private final PeopleDirectory people;

    TrainingActivitiesImpl(
            TrainingSessionRepository sessions,
            AssignmentRepository assignments,
            SessionHistoryRepository history,
            PeopleDirectory people) {
        this.sessions = sessions;
        this.assignments = assignments;
        this.history = history;
        this.people = people;
    }

    @Override
    public List<PlannedActivity> plannedBetween(LocalDate from, LocalDate to, UUID userId) {
        return load(List.of(TrainingSession.PUBLISHED), from, to, userId).stream()
                .map(l -> new PlannedActivity("TRAINING", l.session().id, l.session().createdBy, l.session().sessionDate, place(l.session()), "PLANNED"))
                .toList();
    }

    @Override
    public List<ActivityEntry> activitiesBetween(LocalDate from, LocalDate to, UUID userId) {
        List<Loaded> loaded = load(List.of(TrainingSession.PUBLISHED, TrainingSession.HELD, TrainingSession.CANCELLED), from, to, userId);
        if (loaded.isEmpty()) {
            return List.of();
        }
        Map<UUID, LocalDate> original = originalDates(loaded.stream().map(l -> l.session().id).toList());
        return loaded.stream()
                .map(l -> {
                    TrainingSession s = l.session();
                    return new ActivityEntry(
                            "TRAINING", s.sessionType, s.id, s.createdBy, l.attendees(), s.sessionDate, original.get(s.id), place(s),
                            null, null, null, null, status(s), TrainingSession.HELD.equals(s.status) ? s.sessionDate : null);
                })
                .toList();
    }

    private record Loaded(TrainingSession session, Set<UUID> attendees) {}

    private List<Loaded> load(Collection<String> statuses, LocalDate from, LocalDate to, UUID userId) {
        List<TrainingSession> found = sessions.findByStatusInAndSessionDateBetweenOrderBySessionDateAscStartsAtAsc(statuses, from, to);
        if (found.isEmpty()) {
            return List.of();
        }
        Set<UUID> ids = found.stream().map(s -> s.id).collect(Collectors.toSet());
        List<Assignment> managers = assignments.findBySessionIdIn(ids).stream()
                .filter(a -> a.isRequired() && Assignment.MANAGER.equals(a.personKind))
                .toList();
        Map<UUID, PeopleDirectory.Person> persons = people.managers(managers.stream().map(a -> a.personId).collect(Collectors.toSet()));
        Map<UUID, Set<UUID>> attendeesBySession = new HashMap<>();
        for (Assignment a : managers) {
            PeopleDirectory.Person p = persons.get(a.personId);
            if (p != null && p.userId() != null) {
                attendeesBySession.computeIfAbsent(a.sessionId, k -> new HashSet<>()).add(p.userId());
            }
        }
        return found.stream()
                .map(s -> new Loaded(s, attendeesBySession.getOrDefault(s.id, Set.of())))
                .filter(l -> userId == null || userId.equals(l.session().createdBy) || l.attendees().contains(userId))
                .toList();
    }

    private Map<UUID, LocalDate> originalDates(Collection<UUID> ids) {
        Map<UUID, LocalDate> out = new HashMap<>();
        history.findBySessionIdInAndField(ids, "date").stream()
                .sorted(Comparator.comparing((SessionHistory h) -> h.changedAt))
                .forEach(h -> {
                    if (h.beforeValue != null) {
                        out.putIfAbsent(h.sessionId, LocalDate.parse(h.beforeValue));
                    }
                });
        return out;
    }

    private static String place(TrainingSession s) {
        if ("VIRTUAL".equals(s.venueKind)) {
            return "Online";
        }
        return s.venue;
    }

    private static String status(TrainingSession s) {
        return switch (s.status) {
            case TrainingSession.HELD -> "HELD";
            case TrainingSession.CANCELLED -> "CANCELLED";
            default -> "PLANNED";
        };
    }
}
