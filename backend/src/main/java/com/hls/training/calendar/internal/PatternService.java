package com.hls.training.calendar.internal;

import com.hls.attendance.api.WorkingCalendar;
import com.hls.school.api.ChangeRecorder;
import com.hls.school.api.ConflictException;
import com.hls.school.api.InvalidInputException;
import com.hls.school.api.NotFoundException;
import com.hls.school.api.StaleVersion;
import com.hls.training.api.TrainingSessionChanged;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Standing monthly patterns (spec 024 US2): create, preview, edit from a month, skip and unskip a month, stop. Sessions the
 * pattern generated that a person has edited (hand edited), that were held, or that are cancelled are never rewritten or
 * removed by a pattern change.
 */
@Service("trainingPatternService")
public class PatternService {

    static final String ENTITY = "TRAINING_PATTERN";
    private static final Set<String> GROUP_KINDS = Set.of("SCHOOL", "MANAGER", "ZONE", "ALL_TEACHERS", "ALL_MANAGERS", "ALL");
    private static final Set<String> GROUPS_WITH_REF = Set.of("SCHOOL", "MANAGER", "ZONE");
    private static final int PREVIEW_MONTHS = 6;

    public record DefaultGroup(String kind, UUID ref) {}

    /** Body of create, preview and edit. {@code fromMonth} is read on edit only; {@code startMonth} on create and preview. */
    public record PatternBody(
            String ruleKind,
            Integer nth,
            Integer weekday,
            Integer dayOfMonth,
            LocalTime startsAt,
            LocalTime endsAt,
            String topic,
            String trainerName,
            String trainerContact,
            String venueKind,
            String venue,
            String description,
            DefaultGroup defaultGroup,
            LocalDate startMonth,
            LocalDate fromMonth,
            Long version) {}

    public record SkipBody(LocalDate month, String reason) {}

    public record StopBody(LocalDate fromMonth, Long version) {}

    public record SkipView(LocalDate month, String reason) {}

    public record PatternView(
            UUID id,
            String ruleKind,
            Integer nth,
            Integer weekday,
            Integer dayOfMonth,
            LocalTime startsAt,
            LocalTime endsAt,
            String topic,
            String trainerName,
            String trainerContact,
            String venueKind,
            String venue,
            String description,
            DefaultGroup defaultGroup,
            LocalDate startMonth,
            LocalDate endMonth,
            boolean active,
            Long version,
            List<SkipView> skips) {}

    public record PreviewRow(LocalDate month, LocalDate date, String note, boolean holidayNotice) {}

    private final PatternRepository patterns;
    private final PatternSkipRepository skips;
    private final TrainingSessionRepository sessions;
    private final SessionHistoryRepository history;
    private final AssignmentRepository assignments;
    private final RegistrationRepository registrations;
    private final ReminderSentRepository reminders;
    private final SessionAudience audience;
    private final GenerationService generation;
    private final WorkingCalendar calendar;
    private final ChangeRecorder changes;
    private final org.springframework.context.ApplicationEventPublisher events;
    private final TrainingClock clock;

    PatternService(
            PatternRepository patterns,
            PatternSkipRepository skips,
            TrainingSessionRepository sessions,
            SessionHistoryRepository history,
            AssignmentRepository assignments,
            RegistrationRepository registrations,
            ReminderSentRepository reminders,
            SessionAudience audience,
            GenerationService generation,
            WorkingCalendar calendar,
            ChangeRecorder changes,
            org.springframework.context.ApplicationEventPublisher events,
            TrainingClock clock) {
        this.patterns = patterns;
        this.skips = skips;
        this.sessions = sessions;
        this.history = history;
        this.assignments = assignments;
        this.registrations = registrations;
        this.reminders = reminders;
        this.audience = audience;
        this.generation = generation;
        this.calendar = calendar;
        this.changes = changes;
        this.events = events;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ reads

    @Transactional(readOnly = true)
    public List<PatternView> list() {
        return patterns.findAllByOrderByStartMonthDescCreatedAtDesc().stream().map(this::view).toList();
    }

    @Transactional(readOnly = true)
    public List<PreviewRow> preview(PatternBody body) {
        TrainingPattern p = new TrainingPattern(null, clock.instant());
        fill(p, body);
        LocalDate start = startMonthOf(body.startMonth());
        List<PreviewRow> out = new ArrayList<>();
        for (PatternRules.Occurrence o : PatternRules.preview(start, PREVIEW_MONTHS, p.ruleKind, p.nth, p.weekday, p.dayOfMonth)) {
            boolean holiday = calendar.workingDays(null, o.date(), o.date()).isEmpty();
            out.add(new PreviewRow(o.month(), o.date(), o.note(), holiday));
        }
        return out;
    }

    // ------------------------------------------------------------------ writes

    @Transactional
    public PatternView create(Caller caller, PatternBody body) {
        Instant now = clock.instant();
        TrainingPattern p = new TrainingPattern(caller.userId(), now);
        fill(p, body);
        p.startMonth = startMonthOf(body.startMonth());
        rejectDuplicate(p, null);
        patterns.saveAndFlush(p);
        changes.recordLifecycle(caller.userId(), ENTITY, p.id, "created", describe(p));
        generation.materialise(p.id, clock.today());
        return view(p);
    }

    /** Edits the pattern from {@code fromMonth}: untouched, not-held sessions of that month and later are rewritten. */
    @Transactional
    public PatternView edit(Caller caller, UUID id, PatternBody body) {
        TrainingPattern p = lock(id);
        StaleVersion.check(TrainingPattern.class, id, p.version, body.version());
        if (body.fromMonth() == null) {
            throw new InvalidInputException("Choose the month the change starts from.");
        }
        YearMonth from = YearMonth.from(body.fromMonth());
        String before = describe(p);
        fill(p, body);
        rejectDuplicate(p, id);
        p.updatedAt = clock.instant();
        patterns.saveAndFlush(p);
        changes.record(caller.userId(), ENTITY, id, "rule", before, describe(p));

        for (TrainingSession s : sessions.findByPatternId(id)) {
            YearMonth month = YearMonth.from(s.patternMonth);
            if (month.isBefore(from) || !untouched(s)) {
                continue;
            }
            rewrite(caller, p, s, month);
        }
        generation.materialise(id, clock.today());
        return view(p);
    }

    @Transactional
    public PatternView skip(Caller caller, UUID id, SkipBody body) {
        TrainingPattern p = lock(id);
        if (body == null || body.month() == null) {
            throw new InvalidInputException("Choose the month to skip.");
        }
        String reason = SessionRules.required("A reason", body.reason(), SessionRules.REASON_MAX);
        LocalDate month = body.month().withDayOfMonth(1);
        if (skips.findByPatternIdAndMonth(id, month).isPresent()) {
            throw new ConflictException("That month is already skipped.");
        }
        TrainingSession existing = sessions.findByPatternIdAndPatternMonth(id, month).orElse(null);
        if (existing != null && !untouched(existing)) {
            throw new ConflictException("That month's session was edited or already held, so it cannot be skipped.");
        }
        skips.saveAndFlush(new PatternSkip(id, month, reason, caller.userId(), clock.instant()));
        if (existing != null) {
            existing.status = TrainingSession.SKIPPED;
            existing.skipReason = reason;
            existing.updatedAt = clock.instant();
            sessions.saveAndFlush(existing);
            history.save(new SessionHistory(existing.id, "status", TrainingSession.PUBLISHED, TrainingSession.SKIPPED, reason, caller.userId(), clock.instant()));
            notifyCancelled(existing);
        }
        changes.record(caller.userId(), ENTITY, id, "skip", null, month + ": " + reason);
        return view(p);
    }

    @Transactional
    public PatternView unskip(Caller caller, UUID id, LocalDate monthValue) {
        TrainingPattern p = lock(id);
        LocalDate month = monthValue.withDayOfMonth(1);
        PatternSkip skip = skips.findByPatternIdAndMonth(id, month).orElseThrow(() -> new NotFoundException("That month is not skipped."));
        skips.delete(skip);
        skips.flush();
        TrainingSession existing = sessions.findByPatternIdAndPatternMonth(id, month).orElse(null);
        if (existing != null && TrainingSession.SKIPPED.equals(existing.status)) {
            YearMonth ym = YearMonth.from(month);
            GenerationService.copy(p, existing, ym);
            existing.status = TrainingSession.PUBLISHED;
            existing.skipReason = null;
            existing.updatedAt = clock.instant();
            sessions.saveAndFlush(existing);
            history.save(new SessionHistory(existing.id, "status", TrainingSession.SKIPPED, TrainingSession.PUBLISHED, "Skip removed", caller.userId(), clock.instant()));
        }
        changes.record(caller.userId(), ENTITY, id, "skip", month + ": " + skip.reason, null);
        generation.materialise(id, clock.today());
        return view(p);
    }

    /** Stops the pattern from {@code fromMonth}: untouched later sessions are removed; held and hand-edited ones stay. */
    @Transactional
    public PatternView stop(Caller caller, UUID id, StopBody body) {
        TrainingPattern p = lock(id);
        if (body == null || body.fromMonth() == null) {
            throw new InvalidInputException("Choose the month to stop from.");
        }
        if (body.version() != null) {
            StaleVersion.check(TrainingPattern.class, id, p.version, body.version());
        }
        YearMonth from = YearMonth.from(body.fromMonth());
        if (!from.isAfter(YearMonth.from(p.startMonth))) {
            throw new InvalidInputException("Stop from a month after the pattern starts.");
        }
        if (p.endMonth != null) {
            throw new ConflictException("This pattern is already stopped.");
        }
        p.endMonth = from.minusMonths(1).atDay(1);
        p.updatedAt = clock.instant();
        patterns.saveAndFlush(p);
        for (TrainingSession s : sessions.findByPatternId(id)) {
            if (YearMonth.from(s.patternMonth).isBefore(from) || !untouchedOrSkipped(s)) {
                continue;
            }
            if (TrainingSession.PUBLISHED.equals(s.status)) {
                notifyCancelled(s);
            }
            assignments.deleteAll(assignments.findBySessionId(s.id));
            registrations.deleteAll(registrations.findBySessionId(s.id));
            reminders.deleteBySessionId(s.id);
            sessions.delete(s);
        }
        sessions.flush();
        changes.record(caller.userId(), ENTITY, id, "endMonth", null, p.endMonth);
        return view(p);
    }

    // ------------------------------------------------------------------ helpers

    private TrainingPattern lock(UUID id) {
        return patterns.findForUpdate(id).orElseThrow(() -> new NotFoundException("Pattern not found."));
    }

    /** A generated session nobody has edited and that has not been held or cancelled. */
    private static boolean untouched(TrainingSession s) {
        return !s.handEdited && TrainingSession.PUBLISHED.equals(s.status);
    }

    private static boolean untouchedOrSkipped(TrainingSession s) {
        return !s.handEdited && (TrainingSession.PUBLISHED.equals(s.status) || TrainingSession.SKIPPED.equals(s.status));
    }

    private void rewrite(Caller caller, TrainingPattern p, TrainingSession s, YearMonth month) {
        LocalDate oldDate = s.sessionDate;
        String oldTime = SessionService.time(s.startsAt, s.endsAt);
        String oldTopic = s.topic;
        GenerationService.copy(p, s, month);
        s.updatedAt = clock.instant();
        sessions.saveAndFlush(s);
        Instant now = clock.instant();
        if (!Objects.equals(oldDate, s.sessionDate)) {
            history.save(new SessionHistory(s.id, "date", String.valueOf(oldDate), String.valueOf(s.sessionDate), "Pattern changed", caller.userId(), now));
        }
        String newTime = SessionService.time(s.startsAt, s.endsAt);
        if (!oldTime.equals(newTime)) {
            history.save(new SessionHistory(s.id, "time", oldTime, newTime, "Pattern changed", caller.userId(), now));
        }
        if (!Objects.equals(oldTopic, s.topic)) {
            history.save(new SessionHistory(s.id, "topic", oldTopic, s.topic, "Pattern changed", caller.userId(), now));
        }
        if (!Objects.equals(oldDate, s.sessionDate) || !oldTime.equals(newTime)) {
            reminders.deleteBySessionId(s.id);
            Set<UUID> users = audience.userIds(s.id);
            if (!users.isEmpty()) {
                events.publishEvent(new TrainingSessionChanged(s.id, "MOVED", users, oldDate, s.sessionDate, s.topic));
            }
        }
        changes.record(caller.userId(), SessionService.ENTITY, s.id, "date", oldDate, s.sessionDate);
    }

    private void notifyCancelled(TrainingSession s) {
        Set<UUID> users = audience.userIds(s.id);
        if (!users.isEmpty()) {
            events.publishEvent(new TrainingSessionChanged(s.id, "CANCELLED", users, s.sessionDate, null, s.topic));
        }
    }

    private void rejectDuplicate(TrainingPattern p, UUID selfId) {
        for (TrainingPattern other : patterns.findByEndMonthIsNull()) {
            if (other.id.equals(selfId) || p.endMonth != null) {
                continue;
            }
            if (Objects.equals(other.ruleKind, p.ruleKind)
                    && Objects.equals(other.nth, p.nth)
                    && Objects.equals(other.weekday, p.weekday)
                    && Objects.equals(other.dayOfMonth, p.dayOfMonth)
                    && Objects.equals(other.startsAt, p.startsAt)) {
                throw new ConflictException("A pattern with the same rule and time already exists.");
            }
        }
    }

    /** Validates the body and copies it onto the pattern. */
    private void fill(TrainingPattern p, PatternBody body) {
        if (body == null) {
            throw new InvalidInputException("The pattern is required.");
        }
        try {
            PatternRules.dateIn(YearMonth.of(2026, 1), body.ruleKind(), body.nth(), body.weekday(), body.dayOfMonth());
        } catch (IllegalArgumentException e) {
            throw new InvalidInputException(e.getMessage());
        }
        SessionRules.checkTimes(body.startsAt(), body.endsAt());
        SessionRules.checkVenue(body.venueKind(), body.venue());
        p.ruleKind = body.ruleKind();
        boolean nthRule = PatternRules.NTH_WEEKDAY.equals(body.ruleKind());
        p.nth = nthRule ? body.nth() : null;
        p.weekday = nthRule ? body.weekday() : null;
        p.dayOfMonth = nthRule ? null : body.dayOfMonth();
        p.startsAt = body.startsAt();
        p.endsAt = body.endsAt();
        p.topic = SessionRules.required("The topic", body.topic(), SessionRules.TOPIC_MAX);
        p.trainerName = SessionRules.optional("The trainer name", body.trainerName(), SessionRules.TRAINER_NAME_MAX);
        p.trainerContact = SessionRules.optional("The trainer contact", body.trainerContact(), SessionRules.TRAINER_CONTACT_MAX);
        p.venueKind = body.venueKind();
        p.venue = SessionRules.optional("The venue", body.venue(), SessionRules.VENUE_MAX);
        p.description = SessionRules.optional("The description", body.description(), SessionRules.DESCRIPTION_MAX);
        DefaultGroup g = body.defaultGroup();
        if (g == null || g.kind() == null || g.kind().isBlank()) {
            p.defaultGroupKind = null;
            p.defaultGroupRef = null;
        } else {
            if (!GROUP_KINDS.contains(g.kind())) {
                throw new InvalidInputException("Choose a valid default group.");
            }
            if (GROUPS_WITH_REF.contains(g.kind()) && g.ref() == null) {
                throw new InvalidInputException("Choose which " + g.kind().toLowerCase() + " the default group is.");
            }
            p.defaultGroupKind = g.kind();
            p.defaultGroupRef = GROUPS_WITH_REF.contains(g.kind()) ? g.ref() : null;
        }
    }

    private LocalDate startMonthOf(LocalDate value) {
        LocalDate base = value != null ? value : clock.today();
        return base.withDayOfMonth(1);
    }

    private static String describe(TrainingPattern p) {
        String rule = PatternRules.NTH_WEEKDAY.equals(p.ruleKind)
                ? "nth " + p.nth + " weekday " + p.weekday
                : "day " + p.dayOfMonth;
        return rule + " at " + p.startsAt + "-" + p.endsAt + ", " + p.topic;
    }

    private PatternView view(TrainingPattern p) {
        List<SkipView> skipped = skips.findByPatternId(p.id).stream()
                .sorted(java.util.Comparator.comparing((PatternSkip s) -> s.month))
                .map(s -> new SkipView(s.month, s.reason))
                .toList();
        DefaultGroup group = p.defaultGroupKind == null ? null : new DefaultGroup(p.defaultGroupKind, p.defaultGroupRef);
        return new PatternView(
                p.id, p.ruleKind, p.nth, p.weekday, p.dayOfMonth, p.startsAt, p.endsAt, p.topic, p.trainerName, p.trainerContact,
                p.venueKind, p.venue, p.description, group, p.startMonth, p.endMonth, p.endMonth == null, p.version, skipped);
    }
}
