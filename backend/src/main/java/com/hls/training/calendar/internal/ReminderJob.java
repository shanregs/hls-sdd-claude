package com.hls.training.calendar.internal;

import com.hls.training.api.TrainingReminderDue;
import com.hls.training.api.TrainingSettings;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
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
 * Daily 07:30: reminds the people a published session needs (assigned people and registered Teachers) the configured
 * number of days before it (spec 024 US7). A reminder goes out once per person and offset; a moved session clears its
 * keys (so the new date reminds again) and a cancelled session is never reminded. A second offset of 0 turns that
 * reminder off.
 */
@Component("trainingReminderJob")
public class ReminderJob {

    private static final Logger log = LoggerFactory.getLogger(ReminderJob.class);

    private final TrainingSessionRepository sessions;
    private final AssignmentRepository assignments;
    private final RegistrationRepository registrations;
    private final ReminderSentRepository sent;
    private final PeopleDirectory people;
    private final TrainingSettings settings;
    private final ApplicationEventPublisher events;
    private final TrainingClock clock;
    private final boolean enabled;

    ReminderJob(
            TrainingSessionRepository sessions,
            AssignmentRepository assignments,
            RegistrationRepository registrations,
            ReminderSentRepository sent,
            PeopleDirectory people,
            TrainingSettings settings,
            ApplicationEventPublisher events,
            TrainingClock clock,
            @Value("${hls.training.reminder-job.enabled:true}") boolean enabled) {
        this.sessions = sessions;
        this.assignments = assignments;
        this.registrations = registrations;
        this.sent = sent;
        this.people = people;
        this.settings = settings;
        this.events = events;
        this.clock = clock;
        this.enabled = enabled;
    }

    @Scheduled(cron = "0 30 7 * * *", zone = "${hls.business-timezone:Asia/Kolkata}")
    void scheduled() {
        if (!enabled) {
            return;
        }
        try {
            log.info("Training reminders sent: {}", run(clock.today()));
        } catch (RuntimeException e) {
            log.error("Training reminders failed", e);
        }
    }

    /** Sends the reminders due on {@code today}; returns how many people were reminded. */
    @Transactional
    public int run(LocalDate today) {
        int count = 0;
        List<Integer> offsets = new ArrayList<>();
        offsets.add(settings.reminderDaysFirst());
        if (settings.reminderDaysSecond() > 0 && settings.reminderDaysSecond() != settings.reminderDaysFirst()) {
            offsets.add(settings.reminderDaysSecond());
        }
        for (int offset : offsets) {
            if (offset < 0) {
                continue;
            }
            LocalDate target = today.plusDays(offset);
            List<TrainingSession> due = sessions.findByStatusInAndSessionDateBetweenOrderBySessionDateAscStartsAtAsc(
                    List.of(TrainingSession.PUBLISHED), target, target);
            count += remind(due, offset);
        }
        return count;
    }

    private int remind(List<TrainingSession> due, int offset) {
        if (due.isEmpty()) {
            return 0;
        }
        Set<UUID> ids = due.stream().map(s -> s.id).collect(Collectors.toSet());
        Set<String> already = sent.findBySessionIdIn(ids).stream()
                .filter(r -> r.daysBefore == offset)
                .map(r -> r.sessionId + ":" + r.personKind + ":" + r.personId)
                .collect(Collectors.toSet());
        Map<UUID, List<Assignment>> required = assignments.findBySessionIdIn(ids).stream()
                .filter(Assignment::isRequired)
                .collect(Collectors.groupingBy(a -> a.sessionId));
        Map<UUID, List<Registration>> registered = registrations.findBySessionIdIn(ids).stream()
                .filter(Registration::isRegistered)
                .collect(Collectors.groupingBy(r -> r.sessionId));
        int reminded = 0;
        for (TrainingSession s : due) {
            Set<String> keys = new HashSet<>();
            Set<UUID> teachers = new HashSet<>();
            Set<UUID> managers = new HashSet<>();
            for (Assignment a : required.getOrDefault(s.id, List.of())) {
                (Assignment.MANAGER.equals(a.personKind) ? managers : teachers).add(a.personId);
            }
            registered.getOrDefault(s.id, List.of()).forEach(r -> teachers.add(r.teacherId));
            Map<UUID, PeopleDirectory.Person> persons = people.people(teachers, managers);
            Instant now = clock.instant();
            for (UUID id : teachers) {
                reminded += one(s, Assignment.TEACHER, id, persons.get(id), offset, already, keys, now);
            }
            for (UUID id : managers) {
                reminded += one(s, Assignment.MANAGER, id, persons.get(id), offset, already, keys, now);
            }
        }
        return reminded;
    }

    private int one(
            TrainingSession s,
            String kind,
            UUID id,
            PeopleDirectory.Person person,
            int offset,
            Set<String> already,
            Set<String> keys,
            Instant now) {
        String key = s.id + ":" + kind + ":" + id;
        if (already.contains(key) || !keys.add(key) || person == null || person.userId() == null) {
            return 0;
        }
        sent.save(new ReminderSent(s.id, kind, id, offset, now));
        events.publishEvent(new TrainingReminderDue(s.id, person.userId(), offset, s.sessionDate, s.topic));
        return 1;
    }
}
