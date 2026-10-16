package com.hls.payroll.run.internal;

import com.hls.attendance.api.AttendanceReadApi;
import com.hls.attendance.api.ManagerAttendanceReadApi;
import com.hls.attendance.api.ManagerMonth;
import com.hls.payroll.api.EarnedPay;
import com.hls.payroll.api.PayPolicyView;
import com.hls.payroll.api.PayRules;
import com.hls.payroll.api.PayRules.Kind;
import com.hls.payroll.api.PayRules.PersonRef;
import com.hls.payroll.api.PayableDays;
import com.hls.payroll.api.TotalLossOfPay;
import com.hls.payroll.internal.PersonDirectory;
import com.hls.payroll.run.internal.LineCalculator.AdjustmentInput;
import com.hls.payroll.run.internal.LineCalculator.ManagerFigures;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The background batch of a pay run (spec 013b FR-010, research 3). One virtual-thread task per person does the reads
 * and returns an immutable {@link PersonResult}; a semaphore keeps the tasks from starving the connection pool; the
 * coordinating thread alone writes the lines, in batches, and reports progress. A person whose figures throw becomes a
 * "cannot be paid" line and never stops the others.
 */
@Component
public class RunComputation {

    static final Duration STALE_AFTER = Duration.ofMinutes(10);
    private static final int BATCH = 100;
    private static final Logger LOG = LoggerFactory.getLogger(RunComputation.class);

    private final PayRules payRules;
    private final PersonDirectory people;
    private final AttendanceReadApi attendance;
    private final ManagerAttendanceReadApi managerAttendance;
    private final PayRunRepository runs;
    private final PayRunLineRepository lines;
    private final LineAdjustmentRepository lineAdjustments;
    private final PayAdjustmentRepository adjustments;
    private final RunEventRepository events;
    private final TransactionTemplate tx;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;
    private final Clock clock;
    private final int parallelism;

    RunComputation(
            PayRules payRules,
            PersonDirectory people,
            AttendanceReadApi attendance,
            ManagerAttendanceReadApi managerAttendance,
            PayRunRepository runs,
            PayRunLineRepository lines,
            LineAdjustmentRepository lineAdjustments,
            PayAdjustmentRepository adjustments,
            RunEventRepository events,
            org.springframework.jdbc.core.JdbcTemplate jdbc,
            PlatformTransactionManager transactions,
            Clock clock,
            @Value("${hls.payroll.run.parallelism:6}") int parallelism) {
        this.payRules = payRules;
        this.people = people;
        this.attendance = attendance;
        this.managerAttendance = managerAttendance;
        this.runs = runs;
        this.lines = lines;
        this.lineAdjustments = lineAdjustments;
        this.adjustments = adjustments;
        this.events = events;
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(transactions);
        this.clock = clock;
        this.parallelism = Math.max(1, parallelism);
    }

    /** Starts the computation on a virtual thread of its own; the caller returns at once (the request answers 202). */
    void launch(UUID runId, UUID actor, boolean recompute) {
        Thread.ofVirtual().name("payroll-run-" + runId).start(() -> compute(runId, actor, recompute));
    }

    /** One candidate: who, and what to call them on the screen. */
    private record Candidate(PersonRef ref, String name, String label) {}

    /** Computes (or computes again) the run. Safe to call twice: only the caller that claims the run does the work. */
    public void compute(UUID runId, UUID actor, boolean recompute) {
        try {
            Instant now = clock.instant();
            Integer claimed = tx.execute(s -> runs.claim(runId, now, now.minus(STALE_AFTER)));
            if (claimed == null || claimed == 0) {
                LOG.info("Pay run {} was not claimed (already computing, done or no longer a draft)", runId);
                return;
            }
            run(runId, actor, recompute);
        } catch (RuntimeException e) {
            LOG.error("Pay run {} failed", runId, e);
            fail(runId);
        }
    }

    private void fail(UUID runId) {
        try {
            tx.executeWithoutResult(s -> runs.lockById(runId).ifPresent(run -> {
                if (run.getStatus() == PayRun.Status.DRAFT) {
                    run.setComputeState(PayRun.ComputeState.FAILED, clock.instant());
                }
            }));
        } catch (RuntimeException e) {
            LOG.error("Pay run {} could not be marked failed", runId, e);
        }
    }

    private void run(UUID runId, UUID actor, boolean recompute) {
        PayRun run = tx.execute(s -> runs.findById(runId).orElseThrow());
        YearMonth month = run.getMonth();
        PayPolicyView policy = payRules.policyOn(month.atEndOfMonth());

        List<Candidate> candidates = candidates();
        if (run.getKind() == PayRun.Kind.SUPPLEMENTARY) {
            // only the people not yet paid for the month, computed by the same rules (spec 013b FR-021)
            java.util.Set<PersonRef> paid = paidIn(month);
            candidates = candidates.stream().filter(c -> !paid.contains(c.ref())).toList();
        }
        Map<PersonRef, PayableDays> payable = payRules.payableDays(
                candidates.stream().map(Candidate::ref).toList(), month);
        // a person with no payable date and no defect is not in the run (research 4)
        List<Candidate> inRun = candidates.stream()
                .filter(c -> payable.containsKey(c.ref()) && !payable.get(c.ref()).dates().isEmpty())
                .toList();
        Map<UUID, ManagerMonth> managerMonths = managerAttendance.months(
                inRun.stream().filter(c -> c.ref().kind() == Kind.MANAGER).map(c -> c.ref().id()).toList(), month);

        Map<RecomputeDiff.Key, RecomputeDiff.Before> before = snapshot(runId);
        Map<PersonRef, List<AdjustmentInput>> waiting = waitingAdjustments(runId);
        tx.executeWithoutResult(s -> runs.lockById(runId).ifPresent(r -> r.setProgress(0, inRun.size(), clock.instant())));

        Map<RecomputeDiff.Key, PersonResult> fresh = new LinkedHashMap<>();
        Semaphore permits = new Semaphore(parallelism);
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<PersonResult>> futures = new ArrayList<>();
            for (Candidate candidate : inRun) {
                futures.add(executor.submit(() -> {
                    permits.acquire();
                    try {
                        return one(
                                candidate,
                                month,
                                policy,
                                managerMonths.get(candidate.ref().id()),
                                waiting.getOrDefault(candidate.ref(), List.of()));
                    } finally {
                        permits.release();
                    }
                }));
            }
            List<PersonResult> batch = new ArrayList<>();
            int done = 0;
            for (Future<PersonResult> future : futures) {
                PersonResult result = await(future);
                batch.add(result);
                fresh.put(new RecomputeDiff.Key(result.person().kind(), result.person().id()), result);
                done++;
                if (batch.size() == BATCH) {
                    if (!write(runId, batch, before, done, inRun.size())) {
                        futures.forEach(f -> f.cancel(true));
                        return;
                    }
                    batch = new ArrayList<>();
                }
            }
            if (!write(runId, batch, before, done, inRun.size())) {
                return;
            }
        }
        finish(runId, actor, recompute, policy, before, fresh);
    }

    private static PersonResult await(Future<PersonResult> future) {
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("The pay run was interrupted", e);
        } catch (ExecutionException e) {
            // one() never throws; this is a bug that must not hide
            throw new IllegalStateException("A pay run task failed", e.getCause());
        }
    }

    // ------------------------------------------------------------- the candidates

    private List<Candidate> candidates() {
        List<Candidate> all = new ArrayList<>();
        for (PersonDirectory.TeacherSummary t : people.allTeachers()) {
            all.add(new Candidate(new PersonRef(Kind.TEACHER, t.id()), t.name(), "Teacher"));
        }
        for (PersonDirectory.ManagerSummary m : people.allManagers()) {
            all.add(new Candidate(new PersonRef(Kind.MANAGER, m.id()), m.name(), "Manager"));
        }
        for (PersonDirectory.StaffUser s : people.allStaff()) {
            all.add(new Candidate(new PersonRef(Kind.STAFF, s.id()), s.name(), staffLabel(s.roles())));
        }
        return all;
    }

    private java.util.Set<PersonRef> paidIn(YearMonth month) {
        return new java.util.HashSet<>(jdbc.query(
                "select person_kind, person_id from payroll_paid_month where month = ?",
                (rs, n) -> new PersonRef(Kind.valueOf(rs.getString("person_kind")), (UUID) rs.getObject("person_id")),
                java.sql.Date.valueOf(month.atDay(1))));
    }

    /**
     * Why the person cannot be paid right now, or null when the same checks as a computation would let them be paid.
     * A hint for the owed list only (research 10); it never feeds a figure.
     */
    String blockerNow(PersonRef ref, YearMonth month) {
        try {
            ManagerMonth managerMonth = ref.kind() == Kind.MANAGER ? managerAttendance.month(ref.id(), month) : null;
            String blocked = blocked(ref, month, managerMonth);
            if (blocked != null) {
                return blocked;
            }
            if (payRules.earnedPay(ref, month) instanceof EarnedPay.Cannot e) {
                return e.reason();
            }
            if (payRules.totalLossOfPay(ref, month) instanceof TotalLossOfPay.Cannot d) {
                return d.reason();
            }
            return null;
        } catch (RuntimeException e) {
            return "Could not be checked: " + e.getMessage();
        }
    }

    private static String staffLabel(List<String> roles) {
        return roles.stream()
                .map(r -> r.charAt(0) + r.substring(1).toLowerCase(Locale.ROOT))
                .collect(Collectors.joining(" and "));
    }

    // ------------------------------------------------------------ one person (read-only)

    private PersonResult one(
            Candidate c,
            YearMonth month,
            PayPolicyView policy,
            ManagerMonth managerMonth,
            List<AdjustmentInput> waiting) {
        try {
            return calculate(c, month, policy, managerMonth, waiting);
        } catch (RuntimeException e) {
            LOG.error("Pay run: {} {} could not be worked out", c.ref().kind(), c.ref().id(), e);
            String why = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            return LineCalculator.calculate(new LineCalculator.Inputs(
                    c.ref(),
                    c.name(),
                    c.label(),
                    new EarnedPay.Cannot("unused"),
                    new TotalLossOfPay.Cannot("unused"),
                    Map.of(),
                    policy,
                    List.<AdjustmentInput>of(),
                    null,
                    limit("Could not be worked out: " + why),
                    null));
        }
    }

    private PersonResult calculate(
            Candidate c,
            YearMonth month,
            PayPolicyView policy,
            ManagerMonth managerMonth,
            List<AdjustmentInput> waiting) {
        PersonRef ref = c.ref();
        String blocked = blocked(ref, month, managerMonth);
        EarnedPay earned = payRules.earnedPay(ref, month);
        TotalLossOfPay deduction = payRules.totalLossOfPay(ref, month);
        if (blocked == null && managerMonth instanceof ManagerMonth.NoJoiningDate && deduction instanceof TotalLossOfPay.Cannot cannot) {
            // the missing joining date is the cause to fix first, whatever else is also missing (SC-004)
            blocked = cannot.reason();
        }

        Map<LocalDate, String> reasons = new HashMap<>();
        if (deduction instanceof TotalLossOfPay.Found found) {
            for (TotalLossOfPay.UnpaidDayValue day : found.days()) {
                reasons.put(day.date(), day.dayValue().compareTo(BigDecimal.ONE) >= 0 ? "Full day without pay" : "Half day without pay");
            }
        }
        ManagerFigures figures = null;
        String warning = null;
        if (managerMonth instanceof ManagerMonth.Found found) {
            figures = new ManagerFigures(found.workingDays(), found.unpaid());
            if (earned instanceof EarnedPay.Found e && !e.days().isEmpty()) {
                BigDecimal divisor = BigDecimal.valueOf(e.days().get(e.days().size() - 1).workingDays());
                if (found.workingDays().compareTo(divisor) != 0) {
                    warning = "At the attendance lock the month had " + found.workingDays().stripTrailingZeros().toPlainString()
                            + " working days; the pay divisor is " + divisor.toPlainString() + ".";
                }
            }
        }
        return LineCalculator.calculate(new LineCalculator.Inputs(
                ref, c.name(), c.label(), earned, deduction, reasons, policy, waiting, figures, blocked, warning));
    }

    /** FR-005: a Teacher's or Manager's attendance month must be locked; staff need none. */
    private String blocked(PersonRef ref, YearMonth month, ManagerMonth managerMonth) {
        String monthName = RunInput.monthName(month);
        return switch (ref.kind()) {
            case TEACHER -> attendance.isLocked(ref.id(), month)
                    ? null
                    : "Attendance for " + monthName + " is not locked yet.";
            case MANAGER -> managerMonth instanceof ManagerMonth.Found found && !found.locked()
                    ? "Manager attendance for " + monthName + " is not locked yet."
                    : null;
            case STAFF -> null;
        };
    }

    private static String limit(String text) {
        return text.length() <= 500 ? text : text.substring(0, 497) + "...";
    }

    // ------------------------------------------------------------------ writing

    private Map<RecomputeDiff.Key, RecomputeDiff.Before> snapshot(UUID runId) {
        List<PayRunLine> existing = tx.execute(s -> lines.findByRunId(runId));
        Map<RecomputeDiff.Key, RecomputeDiff.Before> map = new LinkedHashMap<>();
        for (PayRunLine l : existing) {
            map.put(
                    new RecomputeDiff.Key(l.getPersonKind(), l.getPersonId()),
                    new RecomputeDiff.Before(l.getId(), l.getFingerprint(), l.getState(), l.getNet(), l.getPersonName()));
        }
        return map;
    }

    /** Writes one batch; false when the run is no longer a draft (cancelled meanwhile), which stops the work. */
    private boolean write(
            UUID runId,
            List<PersonResult> batch,
            Map<RecomputeDiff.Key, RecomputeDiff.Before> before,
            int done,
            int total) {
        Boolean ok = tx.execute(s -> {
            PayRun run = runs.lockById(runId).orElseThrow();
            if (run.getStatus() != PayRun.Status.DRAFT) {
                return false;
            }
            Instant now = clock.instant();
            Collection<UUID> toUpdate = new ArrayList<>();
            for (PersonResult r : batch) {
                RecomputeDiff.Before old = before.get(new RecomputeDiff.Key(r.person().kind(), r.person().id()));
                if (old == null) {
                    PayRunLine saved = lines.save(new PayRunLine(runId, r, now));
                    saveAdjustments(saved.getId(), r);
                } else if (!old.fingerprint().equals(r.fingerprint())) {
                    toUpdate.add(old.id());
                }
            }
            if (!toUpdate.isEmpty()) {
                Map<UUID, PayRunLine> byId = lines.findAllById(toUpdate).stream()
                        .collect(Collectors.toMap(PayRunLine::getId, l -> l));
                lineAdjustments.deleteByLineIdIn(toUpdate);
                for (PersonResult r : batch) {
                    RecomputeDiff.Before old = before.get(new RecomputeDiff.Key(r.person().kind(), r.person().id()));
                    if (old != null && byId.containsKey(old.id())) {
                        byId.get(old.id()).apply(r, now);
                        saveAdjustments(old.id(), r);
                    }
                }
            }
            run.setProgress(done, total, now);
            return true;
        });
        return Boolean.TRUE.equals(ok);
    }

    private void saveAdjustments(UUID lineId, PersonResult r) {
        for (AdjustmentInput a : r.adjustmentLines()) {
            lineAdjustments.save(new LineAdjustment(lineId, a.id(), a.kind(), a.amount(), a.reason()));
        }
    }

    private void finish(
            UUID runId,
            UUID actor,
            boolean recompute,
            PayPolicyView policy,
            Map<RecomputeDiff.Key, RecomputeDiff.Before> before,
            Map<RecomputeDiff.Key, PersonResult> fresh) {
        RecomputeDiff diff = RecomputeDiff.between(before, fresh);
        tx.executeWithoutResult(s -> {
            PayRun run = runs.lockById(runId).orElseThrow();
            if (run.getStatus() != PayRun.Status.DRAFT) {
                return;
            }
            Instant now = clock.instant();
            List<UUID> gone = RecomputeDiff.removedKeys(before, fresh).stream()
                    .map(k -> before.get(k).id())
                    .toList();
            if (!gone.isEmpty()) {
                lineAdjustments.deleteByLineIdIn(gone);
                lines.deleteAllById(gone);
                lines.flush();
            }
            List<PayRunLine> all = lines.findByRunId(runId);
            RunTotals.refresh(run, all);
            attachAdjustments(runId, fresh);
            run.setPolicy(Json.write(policyMap(policy)));
            if (recompute) {
                run.setRecomputeSummary(diff.toJson());
                events.save(new RunEvent(runId, now, actor, "RECOMPUTED", null, diff.toJson()));
            }
            run.setProgress(all.size(), all.size(), now);
            run.setComputeState(PayRun.ComputeState.DONE, now);
        });
    }

    /** The waiting adjustments a run may take, by person: free ones, and the ones this run already holds (research 11). */
    private Map<PersonRef, List<AdjustmentInput>> waitingAdjustments(UUID runId) {
        Map<PersonRef, List<AdjustmentInput>> map = new HashMap<>();
        for (PayAdjustment a : tx.execute(s -> adjustments.waitingForRun(runId))) {
            map.computeIfAbsent(new PersonRef(a.getPersonKind(), a.getPersonId()), k -> new ArrayList<>())
                    .add(new AdjustmentInput(a.getId(), a.getKind().name(), a.getAmount(), a.getReason()));
        }
        return map;
    }

    /**
     * Marks the adjustments the lines took as attached to this run (they stay WAITING until approval) and lets go of the
     * ones it held but no longer takes, for example a person who cannot be paid any more (spec 4.3).
     */
    private void attachAdjustments(UUID runId, Map<RecomputeDiff.Key, PersonResult> fresh) {
        java.util.Set<UUID> taken = fresh.values().stream()
                .flatMap(r -> r.adjustmentLines().stream())
                .map(AdjustmentInput::id)
                .collect(Collectors.toSet());
        for (PayAdjustment held : adjustments.findByAttachedRunId(runId)) {
            if (!taken.contains(held.getId())) {
                held.detach();
            }
        }
        if (!taken.isEmpty()) {
            for (PayAdjustment a : adjustments.findAllById(taken)) {
                a.attachTo(runId);
            }
        }
    }

    static Map<String, Object> policyMap(PayPolicyView policy) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", policy.id().toString());
        map.put("effectiveOn", policy.effectiveOn().toString());
        map.put("rounding", policy.rounding().name());
        map.put("halfDayFraction", policy.halfDayFraction().toPlainString());
        return map;
    }
}
