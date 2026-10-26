package com.hls.training.calendar.internal;

import com.hls.training.api.TrainingCannotAttend;
import com.hls.training.calendar.internal.PeopleDirectory.Person;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Daily 03:30: an exited Teacher or an inactive Manager no longer owes attendance. Their obligations and registrations on
 * sessions that have not been held are removed with a note, and the Admin and Director are told (spec 024 research.md
 * section 6). Clashing attendance syncs are retried here once attendance exists (US4).
 */
@Component("trainingReconcileJob")
public class ReconcileJob {

    private static final Logger log = LoggerFactory.getLogger(ReconcileJob.class);
    static final String NOTE = "Left the organisation, removed from the session";

    private final TrainingSessionRepository sessions;
    private final AssignmentRepository assignments;
    private final RegistrationRepository registrations;
    private final PeopleDirectory people;
    private final AssignmentService assignmentService;
    private final ApplicationEventPublisher events;
    private final TrainingClock clock;
    private final boolean enabled;

    ReconcileJob(
            TrainingSessionRepository sessions,
            AssignmentRepository assignments,
            RegistrationRepository registrations,
            PeopleDirectory people,
            AssignmentService assignmentService,
            ApplicationEventPublisher events,
            TrainingClock clock,
            @Value("${hls.training.reconcile-job.enabled:true}") boolean enabled) {
        this.sessions = sessions;
        this.assignments = assignments;
        this.registrations = registrations;
        this.people = people;
        this.assignmentService = assignmentService;
        this.events = events;
        this.clock = clock;
        this.enabled = enabled;
    }

    @Scheduled(cron = "0 30 3 * * *", zone = "${hls.business-timezone:Asia/Kolkata}")
    void scheduled() {
        if (!enabled) {
            return;
        }
        try {
            log.info("Training reconcile removed {} obligations", run());
        } catch (RuntimeException e) {
            log.error("Training reconcile failed", e);
        }
    }

    /** Removes the obligations and registrations of people who have left from sessions not yet held; returns the count. */
    @Transactional
    public int run() {
        LocalDate today = clock.today();
        List<TrainingSession> open = sessions
                .findByStatusAndSessionDateGreaterThanEqualOrderBySessionDateAscStartsAtAsc(TrainingSession.PUBLISHED, today);
        if (open.isEmpty()) {
            return 0;
        }
        Map<UUID, TrainingSession> byId = open.stream().collect(Collectors.toMap(s -> s.id, s -> s));
        List<Assignment> required = assignments.findBySessionIdIn(byId.keySet()).stream().filter(Assignment::isRequired).toList();
        List<Registration> registered = registrations.findBySessionIdIn(byId.keySet()).stream().filter(Registration::isRegistered).toList();
        Map<UUID, Person> teachers = people.teachers(
                java.util.stream.Stream.concat(
                                required.stream().filter(a -> Assignment.TEACHER.equals(a.personKind)).map(a -> a.personId),
                                registered.stream().map(r -> r.teacherId))
                        .collect(Collectors.toSet()));
        Map<UUID, Person> managers = people.managers(
                required.stream().filter(a -> Assignment.MANAGER.equals(a.personKind)).map(a -> a.personId).collect(Collectors.toSet()));
        int removed = 0;
        for (Assignment a : required) {
            Person p = Assignment.MANAGER.equals(a.personKind) ? managers.get(a.personId) : teachers.get(a.personId);
            if (p == null || !gone(a.personKind, p)) {
                continue;
            }
            TrainingSession s = byId.get(a.sessionId);
            assignmentService.markRemoved(a, null, NOTE);
            assignments.save(a);
            events.publishEvent(new TrainingCannotAttend(s.id, p.name(), NOTE));
            removed++;
        }
        Set<UUID> gone = teachers.values().stream().filter(p -> gone(Assignment.TEACHER, p)).map(Person::id).collect(Collectors.toSet());
        for (Registration r : registered) {
            if (gone.contains(r.teacherId)) {
                r.status = Registration.CANCELLED;
                r.cancelledAt = clock.instant();
                registrations.save(r);
                TrainingSession s = byId.get(r.sessionId);
                events.publishEvent(new TrainingCannotAttend(s.id, teachers.get(r.teacherId).name(), NOTE));
                removed++;
            }
        }
        return removed;
    }

    private static boolean gone(String kind, Person p) {
        return Assignment.MANAGER.equals(kind) ? !p.active() : "EXITED".equals(p.status());
    }
}
