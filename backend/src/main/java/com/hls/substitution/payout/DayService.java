package com.hls.substitution.payout;

import com.hls.attendance.api.WorkingCalendar;
import com.hls.identity.user.Role;
import com.hls.payroll.api.DayRates;
import com.hls.school.api.CallerContext;
import com.hls.school.api.ChangeRecorder;
import com.hls.school.api.ConflictException;
import com.hls.school.api.InvalidInputException;
import com.hls.school.api.NotFoundException;
import com.hls.school.api.SchoolDirectory;
import com.hls.school.api.StaleVersion;
import com.hls.substitution.api.SubstitutionAssigned;
import com.hls.substitution.api.SubstitutionEnded;
import com.hls.substitution.internal.SubstitutionRepository;
import com.hls.substitution.internal.SubstitutionRequirement;
import com.hls.substitution.internal.SubstitutionScope;
import com.hls.substitution.payout.DayEvent.Cause;
import com.hls.substitution.payout.DayEvent.Field;
import com.hls.substitution.payout.DayStatusDeriver.CorrectionState;
import com.hls.substitution.payout.DayStatusDeriver.Facts;
import com.hls.substitution.payout.DayStatusDeriver.RunHolding;
import com.hls.teacher.api.TeacherDirectory;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Substitute day attendance (spec 017b FR-007 to FR-010): a day record per live allocation, marked Present or Absent by
 * the Zone Manager (or Admin or Director), locked per substitute and month, with an append-only event for every change.
 * There is no path for a substitute to mark their own day.
 */
@Service
public class DayService {

    static final String ENTITY = "SUBSTITUTE_DAY";
    static final String LOCK_ENTITY = "SUBSTITUTE_MONTH";
    static final int MAX_REASON = 500;

    /** The outcome of a bulk lock: months locked and the substitutes blocked, with why. */
    public record BulkLockResult(int locked, int alreadyLocked, List<Blocked> blocked) {}

    public record Blocked(UUID substituteTeacherId, String reason) {}

    private final SubstituteDayRepository days;
    private final DayEventRepository events;
    private final MonthLockRepository locks;
    private final SubstitutionRepository requirements;
    private final SubstitutionScope scope;
    private final SchoolDirectory schools;
    private final TeacherDirectory teachers;
    private final DayRates dayRates;
    private final WorkingCalendar calendar;
    private final ChangeRecorder changes;
    private final Clock clock;

    public DayService(
            SubstituteDayRepository days,
            DayEventRepository events,
            MonthLockRepository locks,
            SubstitutionRepository requirements,
            SubstitutionScope scope,
            SchoolDirectory schools,
            TeacherDirectory teachers,
            DayRates dayRates,
            WorkingCalendar calendar,
            ChangeRecorder changes,
            Clock clock) {
        this.days = days;
        this.events = events;
        this.locks = locks;
        this.requirements = requirements;
        this.scope = scope;
        this.schools = schools;
        this.teachers = teachers;
        this.dayRates = dayRates;
        this.calendar = calendar;
        this.changes = changes;
        this.clock = clock;
    }

    // ------------------------------------------------------- events from 017a

    /** A substitute was assigned: the day record is created, or revived if the same substitute is assigned again. */
    @Transactional
    public void onAssigned(SubstitutionAssigned event) {
        SubstitutionRequirement requirement = requirements
                .findById(event.requirementId())
                .orElseThrow(() -> new NotFoundException("Substitution requirement not found."));
        UUID zone = schools.school(event.schoolId())
                .map(SchoolDirectory.SchoolInfo::zoneId)
                .orElseThrow(() -> new NotFoundException("School not found."));
        Instant now = clock.instant();
        Optional<SubstituteDay> existing =
                days.findByRequirementIdAndSubstituteTeacherId(event.requirementId(), event.substituteTeacherId());
        if (existing.isPresent()) {
            SubstituteDay day = existing.get();
            if (!day.isLive()) {
                day.reassign(zone, requirement.getTeacherId(), requirement.getDayValue(), now);
                event(day, Field.LIVE, "false", "true", null, Cause.EVENT_ASSIGNED, "assigned again");
                refresh(day, now, Cause.EVENT_ASSIGNED);
                days.save(day);
            }
            return;
        }
        SubstituteDay day = days.saveAndFlush(new SubstituteDay(
                event.requirementId(),
                event.substituteTeacherId(),
                event.schoolId(),
                zone,
                requirement.getTeacherId(),
                event.date(),
                requirement.getDayValue(),
                "ALLOCATION",
                now));
        event(day, Field.LIVE, null, "true", null, Cause.EVENT_ASSIGNED, "allocated");
        refresh(day, now, Cause.EVENT_ASSIGNED);
        days.save(day);
    }

    /** An allocation ended: the day is cancelled and kept for the record. */
    @Transactional
    public void onEnded(SubstitutionEnded event) {
        days.findByRequirementIdAndSubstituteTeacherId(event.requirementId(), event.substituteTeacherId())
                .filter(SubstituteDay::isLive)
                .ifPresent(day -> {
                    Instant now = clock.instant();
                    day.cancel(event.reason(), now);
                    event(day, Field.LIVE, "true", "false", null, Cause.EVENT_ENDED, event.reason());
                    refresh(day, now, Cause.EVENT_ENDED);
                    days.save(day);
                });
    }

    // --------------------------------------------------------------- marking

    /** Marks a day Present, Absent or Not marked. Needs Edit and the School in scope (404 otherwise). */
    @Transactional
    public SubstituteDay mark(
            UUID actor, Set<Role> roles, UUID dayId, DayAttendance attendance, Long version) {
        if (attendance == null) {
            throw new InvalidInputException("The attendance is required.");
        }
        SubstituteDay day = days.lockById(dayId).orElseThrow(() -> new NotFoundException("Substitute day not found."));
        scope.require(actor, roles, day.getSchoolId());
        StaleVersion.check(SubstituteDay.class, dayId, day.getVersion(), version);
        if (!day.isLive()) {
            throw new ConflictException("This allocation was cancelled, so the day cannot be marked.");
        }
        LocalDate today = calendar.businessToday();
        if (day.getDayDate().isAfter(today)) {
            throw new ConflictException("A day in the future cannot be marked yet.");
        }
        if (isLocked(day.getSubstituteTeacherId(), day.month())) {
            throw new ConflictException("This month is locked for the substitute. Ask the Admin or Director to reopen it.");
        }
        DayAttendance before = day.getAttendance();
        if (before == attendance) {
            return day;
        }
        Instant now = clock.instant();
        day.mark(attendance, actor, now);
        event(day, Field.ATTENDANCE, before.name(), attendance.name(), actor, Cause.MARK, null);
        changes.record(actor, ENTITY, day.getId(), "attendance", before, attendance);
        refresh(day, now, Cause.MARK);
        return days.saveAndFlush(day);
    }

    // ------------------------------------------------------------- lock month

    @Transactional(readOnly = true)
    public boolean isLocked(UUID substituteTeacherId, YearMonth month) {
        return locks.findBySubstituteTeacherIdAndMonth(substituteTeacherId, month.atDay(1))
                .map(MonthLock::isLocked)
                .orElse(false);
    }

    /** Locks one substitute-month once it has ended and every live day is marked. 409 lists the unmarked days. */
    @Transactional
    public void lock(UUID actor, UUID substituteTeacherId, YearMonth month) {
        requireSubstitute(substituteTeacherId);
        String blockedBy = lockBlocker(substituteTeacherId, month);
        if (blockedBy != null) {
            throw new ConflictException(blockedBy);
        }
        doLock(actor, substituteTeacherId, month);
    }

    /** Locks every complete substitute-month in the month; the others are returned with the reason. */
    @Transactional
    public BulkLockResult lockAll(UUID actor, YearMonth month) {
        int locked = 0;
        int already = 0;
        List<Blocked> blocked = new ArrayList<>();
        for (UUID teacherId : days.substitutesWithDays(month.atDay(1), month.atEndOfMonth())) {
            if (isLocked(teacherId, month)) {
                already++;
                continue;
            }
            String reason = lockBlocker(teacherId, month);
            if (reason == null) {
                doLock(actor, teacherId, month);
                locked++;
            } else {
                blocked.add(new Blocked(teacherId, reason));
            }
        }
        return new BulkLockResult(locked, already, blocked);
    }

    /** Reopens a locked substitute-month. The reason is required and kept (at most 500 characters). */
    @Transactional
    public void reopen(UUID actor, UUID substituteTeacherId, YearMonth month, String reason) {
        requireSubstitute(substituteTeacherId);
        if (reason == null || reason.isBlank()) {
            throw new InvalidInputException("A reason is required to reopen a month.");
        }
        String cleanReason = reason.trim();
        if (cleanReason.length() > MAX_REASON) {
            throw new InvalidInputException("The reason can be at most " + MAX_REASON + " characters.");
        }
        MonthLock lock = locks.findBySubstituteTeacherIdAndMonth(substituteTeacherId, month.atDay(1))
                .filter(MonthLock::isLocked)
                .orElseThrow(() -> new ConflictException("This month is not locked."));
        Instant now = clock.instant();
        lock.reopen(actor, cleanReason, now);
        locks.save(lock);
        changes.record(actor, LOCK_ENTITY, substituteTeacherId, month + " lock", "locked", "reopened: " + cleanReason);
        for (SubstituteDay day : days.ofTeacher(substituteTeacherId, month.atDay(1), month.atEndOfMonth())) {
            event(day, Field.REOPEN, "locked", "open", actor, Cause.LOCK, cleanReason);
            refresh(day, now, Cause.LOCK);
            days.save(day);
        }
    }

    /** Why the month cannot be locked yet, or null when it can. */
    private String lockBlocker(UUID substituteTeacherId, YearMonth month) {
        if (isLocked(substituteTeacherId, month)) {
            return "This month is already locked.";
        }
        if (!month.isBefore(YearMonth.from(calendar.businessToday()))) {
            return "A month can be locked only after it has ended.";
        }
        List<SubstituteDay> unmarked = days.unmarkedOf(substituteTeacherId, month.atDay(1), month.atEndOfMonth());
        if (!unmarked.isEmpty()) {
            return "Attendance is not marked for: "
                    + unmarked.stream().map(d -> d.getDayDate().toString()).collect(Collectors.joining(", ")) + ".";
        }
        return null;
    }

    private void doLock(UUID actor, UUID substituteTeacherId, YearMonth month) {
        Instant now = clock.instant();
        MonthLock lock = locks.findBySubstituteTeacherIdAndMonth(substituteTeacherId, month.atDay(1))
                .orElseGet(() -> new MonthLock(substituteTeacherId, month));
        lock.lock(actor, now);
        locks.save(lock);
        changes.record(actor, LOCK_ENTITY, substituteTeacherId, month + " lock", "open", "locked");
        for (SubstituteDay day : days.ofTeacher(substituteTeacherId, month.atDay(1), month.atEndOfMonth())) {
            event(day, Field.LOCK, "open", "locked", actor, Cause.LOCK, null);
            refresh(day, now, Cause.LOCK);
            days.save(day);
        }
    }

    private void requireSubstitute(UUID teacherId) {
        if (!teachers.substituteTeacherIds().contains(teacherId)) {
            throw new NotFoundException("Substitute not found.");
        }
    }

    // ---------------------------------------------------------------- status

    /**
     * Works out the status of the day from the facts and stores it (with an event when it changed). The run facts
     * default to what the day already holds; the reconciliation (spec 017b FR-017) supplies the live ones.
     */
    void refresh(SubstituteDay day, Instant now, Cause cause) {
        Pricing pricing = pricing(day);
        BigDecimal amount = pricing.rate() == null
                ? null
                : pricing.rate().multiply(day.getDayValue()).setScale(2, java.math.RoundingMode.HALF_UP);
        day.setPricing(pricing.rate(), amount);
        RunHolding holding = day.getRunId() == null
                ? RunHolding.NONE
                : day.getPaidAmount() != null ? RunHolding.APPROVED : RunHolding.DRAFT;
        CorrectionState correction = day.getCorrectionRef() == null ? CorrectionState.NONE : CorrectionState.RAISED;
        DayStatus next = DayStatusDeriver.derive(new Facts(
                day.isLive(),
                day.getAttendance(),
                isLocked(day.getSubstituteTeacherId(), day.month()),
                pricing.priceable(),
                holding,
                false,
                correction));
        DayStatus before = day.getStatus();
        String reason = next == DayStatus.CANNOT_PAY ? pricing.reason() : null;
        day.setStatus(next, reason, now);
        if (before != next) {
            events.save(new DayEvent(day.getId(), now, Field.STATUS, before.name(), next.name(), null, cause, reason));
        }
    }

    private record Pricing(boolean priceable, BigDecimal rate, String reason) {}

    private Pricing pricing(SubstituteDay day) {
        if (!teachers.activeSubstituteTeacherIds().contains(day.getSubstituteTeacherId())) {
            return new Pricing(false, null, "The substitute is not an Active Substitute.");
        }
        Optional<UUID> designation = teachers.currentDesignation(day.getSubstituteTeacherId());
        if (designation.isEmpty()) {
            return new Pricing(false, null, "The substitute has no designation.");
        }
        return switch (dayRates.rateOn(designation.get(), day.getDayDate())) {
            case DayRates.Found f -> new Pricing(true, f.perDay(), null);
            case DayRates.None n -> new Pricing(false, null, n.reason());
        };
    }

    private void event(
            SubstituteDay day, Field field, String from, String to, UUID actor, Cause cause, String note) {
        events.save(new DayEvent(day.getId(), clock.instant(), field, from, to, actor, cause, note));
    }

    /** Whether the caller is org-wide (used by the web layer to decide the actions it offers). */
    public static boolean orgWide(Set<Role> roles) {
        return CallerContext.isOrgWide(roles);
    }
}
