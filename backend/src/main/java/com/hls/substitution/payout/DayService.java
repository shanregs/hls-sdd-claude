package com.hls.substitution.payout;

import com.hls.attendance.api.WorkingCalendar;
import com.hls.identity.user.Role;
import com.hls.payroll.api.CorrectionRaising;
import com.hls.payroll.api.DayRatePay;
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
    private final DayRatePay pay;
    private final CorrectionRaising corrections;
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
            DayRatePay pay,
            CorrectionRaising corrections,
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
        this.pay = pay;
        this.corrections = corrections;
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

    // ----------------------------------------------------------------- repair

    /**
     * Creates the day record of a live credit that has none (a missed event). Idempotent: false when it already exists or
     * the credit is not a live one of a Substitute.
     */
    @Transactional
    public boolean createIfMissing(SubstitutionRequirement credit) {
        UUID substitute = credit.getSubstituteTeacherId();
        if (substitute == null
                || days.findByRequirementIdAndSubstituteTeacherId(credit.getId(), substitute).isPresent()
                || !teachers.substituteTeacherIds().contains(substitute)) {
            return false;
        }
        UUID zone = schools.school(credit.getSchoolId())
                .map(SchoolDirectory.SchoolInfo::zoneId)
                .orElseThrow(() -> new NotFoundException("School not found."));
        Instant now = clock.instant();
        SubstituteDay day = days.saveAndFlush(new SubstituteDay(
                credit.getId(),
                substitute,
                credit.getSchoolId(),
                zone,
                credit.getTeacherId(),
                credit.getDutyDate(),
                credit.getDayValue(),
                "ALLOCATION",
                now));
        event(day, Field.LIVE, null, "true", null, Cause.JOB, "recreated by the repair job");
        refresh(day, now, Cause.JOB);
        days.save(day);
        return true;
    }

    /** Cancels a live day whose allocation no longer stands (a missed end event). False when it is still a live credit. */
    @Transactional
    public boolean cancelIfStale(SubstituteDay day) {
        if (!day.isLive()) {
            return false;
        }
        boolean stands = requirements
                .findById(day.getRequirementId())
                .filter(r -> r.getStatus() == com.hls.substitution.internal.RequirementStatus.ASSIGNED)
                .filter(r -> day.getSubstituteTeacherId().equals(r.getSubstituteTeacherId()))
                .isPresent();
        if (stands) {
            return false;
        }
        Instant now = clock.instant();
        day.cancel("allocation ended (repaired)", now);
        event(day, Field.LIVE, "true", "false", null, Cause.JOB, "the allocation no longer stands");
        refresh(day, now, Cause.JOB);
        days.save(day);
        return true;
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

    // ------------------------------------------------------------ corrections

    static final String CORRECTION_PREFIX = "SUBSTITUTE-CORRECTION:";

    /**
     * An Admin raises a Correction for a day that changed after the month was paid (spec 017b FR-020). The amount is
     * the Admin's, bounded by payroll's own rule; it is linked to the day by the source reference so the day becomes
     * Corrected when a run applies it. Raised once per day.
     */
    @Transactional
    public SubstituteDay raiseCorrection(UUID actor, UUID dayId, BigDecimal amount, String reason) {
        SubstituteDay day = days.lockById(dayId).orElseThrow(() -> new NotFoundException("Substitute day not found."));
        if (day.getCorrectionRef() != null) {
            throw new ConflictException("A Correction was already raised for this day.");
        }
        if (day.getStatus() != DayStatus.CORRECTION_NEEDED) {
            throw new ConflictException("A Correction is raised only for a day marked \"Correction needed\".");
        }
        if (amount == null || amount.signum() == 0) {
            throw new InvalidInputException("The Correction amount is required and cannot be zero.");
        }
        if (reason == null || reason.isBlank()) {
            throw new InvalidInputException("A reason is required for a Correction.");
        }
        if (reason.trim().length() > MAX_REASON) {
            throw new InvalidInputException("The reason can be at most " + MAX_REASON + " characters.");
        }
        String ref = CORRECTION_PREFIX + dayId;
        CorrectionRaising.Result result =
                corrections.raiseForTeacher(actor, day.getSubstituteTeacherId(), amount, reason.trim(), ref);
        if (!result.accepted()) {
            throw new ConflictException(result.refusal());
        }
        Instant now = clock.instant();
        day.setCorrectionRef(ref);
        DayStatus before = day.getStatus();
        day.setStatus(DayStatus.CORRECTION_RAISED, null, now);
        event(day, Field.CORRECTION, before.name(), amount.toPlainString(), actor, Cause.CORRECTION, reason.trim());
        changes.record(actor, ENTITY, dayId, "correction", null, amount.toPlainString() + ": " + reason.trim());
        return days.saveAndFlush(day);
    }

    /** Payroll applied a Correction: the day it was raised for is Corrected. */
    @Transactional
    public void onCorrectionApplied(String sourceRef) {
        if (sourceRef == null || !sourceRef.startsWith(CORRECTION_PREFIX)) {
            return;
        }
        UUID dayId;
        try {
            dayId = UUID.fromString(sourceRef.substring(CORRECTION_PREFIX.length()));
        } catch (IllegalArgumentException e) {
            return;
        }
        days.findById(dayId).ifPresent(day -> {
            Instant now = clock.instant();
            DayStatus before = day.getStatus();
            day.setStatus(DayStatus.CORRECTED, null, now);
            events.save(new DayEvent(
                    dayId, now, Field.CORRECTION, before.name(), DayStatus.CORRECTED.name(), null, Cause.RUN_APPROVED, "applied"));
            days.save(day);
        });
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

        // what the pay runs hold for this day: the strongest of the runs (approved over awaiting over draft)
        RunHolding holding = RunHolding.NONE;
        DayRatePay.PaidDay held = null;
        if (day.getId() != null) {
            for (DayRatePay.PaidDay p : pay.daysInRuns(List.of(day.getId()))) {
                RunHolding h = switch (p.state()) {
                    case APPROVED -> RunHolding.APPROVED;
                    case AWAITING_APPROVAL -> RunHolding.AWAITING_APPROVAL;
                    case DRAFT -> RunHolding.DRAFT;
                };
                if (h.ordinal() >= holding.ordinal()) {
                    holding = h;
                    held = p;
                }
            }
        }
        UUID runBefore = day.getRunId();
        if (held == null) {
            day.setRun(null, null, null);
        } else {
            day.setRun(
                    held.runId(),
                    held.runMonth().atDay(1),
                    holding == RunHolding.APPROVED ? held.amount().setScale(2, java.math.RoundingMode.HALF_UP) : null);
        }
        if (day.getId() != null && !java.util.Objects.equals(runBefore, day.getRunId())) {
            events.save(new DayEvent(
                    day.getId(), now, Field.RUN, String.valueOf(runBefore), String.valueOf(day.getRunId()), null, cause, holding.name()));
        }
        CorrectionState correction = day.getCorrectionRef() == null
                ? CorrectionState.NONE
                : day.getStatus() == DayStatus.CORRECTED ? CorrectionState.APPLIED : CorrectionState.RAISED;
        DayStatus next = DayStatusDeriver.derive(new Facts(
                day.isLive(),
                day.getAttendance(),
                isLocked(day.getSubstituteTeacherId(), day.month()),
                pricing.priceable(),
                holding,
                pay.primaryRunApproved(day.month()),
                holding == RunHolding.NONE && pay.paidForMonth(day.getSubstituteTeacherId(), day.month()),
                correction));
        DayStatus before = day.getStatus();
        String reason = switch (next) {
            case CANNOT_PAY -> pricing.reason();
            case CORRECTION_NEEDED -> day.isLive() && day.getAttendance() == DayAttendance.PRESENT && holding == RunHolding.NONE
                    ? "Added after the month was paid."
                    : "Changed after it was paid.";
            default -> null;
        };
        day.setStatus(next, reason, now);
        if (before != next && day.getId() != null) {
            events.save(new DayEvent(day.getId(), now, Field.STATUS, before.name(), next.name(), null, cause, reason));
        }
    }

    /** Works the status of every day of the month out again, for example after a run was submitted, approved or returned. */
    @Transactional
    public int refreshMonth(YearMonth month, Cause cause) {
        // the run's own change is still unflushed when its event arrives; payroll is read with SQL, so write it out first
        days.flush();
        Instant now = clock.instant();
        int changed = 0;
        for (SubstituteDay day : days.between(month.atDay(1), month.atEndOfMonth())) {
            DayStatus before = day.getStatus();
            refresh(day, now, cause);
            days.save(day);
            if (before != day.getStatus()) {
                changed++;
            }
        }
        return changed;
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
