package com.hls.training.calendar.internal;

import com.hls.training.api.TrainingSettings;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Materialises the sessions of the standing patterns up to the horizon (spec 024 US2, FR-005). The work is idempotent:
 * a month that already has a row for the pattern is left alone, so running the job twice a day, or after a gap, never
 * duplicates or misses a month. Each pattern is locked while it is filled so two runs cannot both create a month.
 * A start month in the past is not back-filled: generation begins at the current month.
 */
@Service("trainingGenerationService")
public class GenerationService {

    private final PatternRepository patterns;
    private final PatternSkipRepository skips;
    private final TrainingSessionRepository sessions;
    private final TrainingSettings settings;
    private final TrainingClock clock;

    GenerationService(
            PatternRepository patterns,
            PatternSkipRepository skips,
            TrainingSessionRepository sessions,
            TrainingSettings settings,
            TrainingClock clock) {
        this.patterns = patterns;
        this.skips = skips;
        this.sessions = sessions;
        this.settings = settings;
        this.clock = clock;
    }

    /** Fills every open-ended and still-running pattern as of {@code today}; returns the number of sessions created. */
    @Transactional
    public int run(LocalDate today) {
        int created = 0;
        for (TrainingPattern p : patterns.findAll()) {
            created += materialise(p.id, today);
        }
        return created;
    }

    @Transactional
    public int runToday() {
        return run(clock.today());
    }

    /** Fills one pattern; returns the number of sessions created. */
    @Transactional
    public int materialise(UUID patternId, LocalDate today) {
        TrainingPattern pattern = patterns.findForUpdate(patternId).orElse(null);
        if (pattern == null) {
            return 0;
        }
        YearMonth current = YearMonth.from(today);
        YearMonth first = YearMonth.from(pattern.startMonth);
        if (first.isBefore(current)) {
            first = current;
        }
        YearMonth last = current.plusMonths(settings.generationHorizonMonths());
        if (pattern.endMonth != null && YearMonth.from(pattern.endMonth).isBefore(last)) {
            last = YearMonth.from(pattern.endMonth);
        }
        List<PatternSkip> skipped = skips.findByPatternId(patternId);
        int created = 0;
        for (YearMonth month = first; !month.isAfter(last); month = month.plusMonths(1)) {
            LocalDate monthStart = month.atDay(1);
            if (sessions.findByPatternIdAndPatternMonth(patternId, monthStart).isPresent()) {
                continue;
            }
            TrainingSession session = newSession(pattern, month);
            PatternSkip skip = skipped.stream().filter(s -> s.month.equals(monthStart)).findFirst().orElse(null);
            if (skip != null) {
                session.status = TrainingSession.SKIPPED;
                session.skipReason = skip.reason;
            }
            sessions.saveAndFlush(session);
            created++;
            // The default group snapshot is applied here once the assignment service exists (spec 024 T035).
        }
        return created;
    }

    private TrainingSession newSession(TrainingPattern p, YearMonth month) {
        TrainingSession s = new TrainingSession(p.createdBy, clock.instant());
        s.sessionType = TrainingSession.MONTHLY;
        s.status = TrainingSession.PUBLISHED;
        s.patternId = p.id;
        s.patternMonth = month.atDay(1);
        s.handEdited = false;
        copy(p, s, month);
        return s;
    }

    /** Copies the pattern's fields and the month's date onto a session. */
    static void copy(TrainingPattern p, TrainingSession s, YearMonth month) {
        s.sessionDate = PatternRules.dateIn(month, p.ruleKind, p.nth, p.weekday, p.dayOfMonth);
        s.startsAt = p.startsAt;
        s.endsAt = p.endsAt;
        s.topic = p.topic;
        s.trainerName = p.trainerName;
        s.trainerContact = p.trainerContact;
        s.venueKind = p.venueKind;
        s.venue = p.venue;
        s.description = p.description;
    }
}
