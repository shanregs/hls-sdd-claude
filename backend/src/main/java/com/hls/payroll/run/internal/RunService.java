package com.hls.payroll.run.internal;

import com.hls.attendance.api.WorkingCalendar;
import com.hls.identity.user.AppUser;
import com.hls.identity.user.AppUserRepository;
import com.hls.payroll.api.PayRules.Kind;
import com.hls.payroll.run.internal.RunViews.AdjustmentLine;
import com.hls.payroll.run.internal.RunViews.EventView;
import com.hls.payroll.run.internal.RunViews.LineView;
import com.hls.payroll.run.internal.RunViews.Person;
import com.hls.payroll.run.internal.RunViews.RunView;
import com.hls.payroll.run.internal.RunViews.Totals;
import com.hls.school.api.ChangeRecorder;
import com.hls.school.api.ConflictException;
import com.hls.school.api.InvalidInputException;
import com.hls.school.api.NotFoundException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * The lifecycle of a pay run that spec 013b US1 needs: start, compute again, cancel, and the reads. Every state change
 * runs under a row lock on the run (research 8) and writes an event and an audit entry in the same transaction.
 */
@Service
public class RunService {

    static final String ENTITY = "PAYROLL_RUN";
    private static final int MAX_PAGE_SIZE = 200;

    private final PayRunRepository runs;
    private final PayRunLineRepository lines;
    private final LineAdjustmentRepository lineAdjustments;
    private final RunEventRepository events;
    private final RunComputation computation;
    private final ChangeRecorder changes;
    private final WorkingCalendar calendar;
    private final AppUserRepository users;
    private final Clock clock;

    RunService(
            PayRunRepository runs,
            PayRunLineRepository lines,
            LineAdjustmentRepository lineAdjustments,
            RunEventRepository events,
            RunComputation computation,
            ChangeRecorder changes,
            WorkingCalendar calendar,
            AppUserRepository users,
            Clock clock) {
        this.runs = runs;
        this.lines = lines;
        this.lineAdjustments = lineAdjustments;
        this.events = events;
        this.computation = computation;
        this.changes = changes;
        this.calendar = calendar;
        this.users = users;
        this.clock = clock;
    }

    // ----------------------------------------------------------------- commands

    /** Starts the primary run of an ended month. The computation runs in the background after the commit. */
    @Transactional
    public RunView start(UUID actor, String monthText, boolean supplementary) {
        YearMonth month = RunInput.endedMonth(RunInput.month(monthText), calendar.businessToday());
        if (supplementary) {
            // spec 013b US5 (T044) adds the supplementary run; until then it is refused rather than half done
            throw new ConflictException("A supplementary run is not available yet.");
        }
        runs.findByMonthOrderByKindAscSequenceAsc(month.atDay(1)).stream()
                .filter(r -> r.getKind() == PayRun.Kind.PRIMARY && r.getStatus() != PayRun.Status.CANCELLED)
                .findFirst()
                .ifPresent(r -> {
                    throw new ConflictException("A pay run already exists for " + RunInput.monthName(month) + " ("
                            + statusName(r.getStatus()) + "). Cancel it first if it is a draft.");
                });
        Instant now = clock.instant();
        int sequence = runs.maxSequence(month.atDay(1), PayRun.Kind.PRIMARY) + 1;
        PayRun run;
        try {
            run = runs.saveAndFlush(new PayRun(month, PayRun.Kind.PRIMARY, sequence, actor, now));
        } catch (DataIntegrityViolationException e) {
            // two starts at the same moment: the partial unique index let one in
            throw new ConflictException("A pay run already exists for " + RunInput.monthName(month) + ".");
        }
        events.save(new RunEvent(run.getId(), now, actor, "STARTED", null, null));
        changes.recordLifecycle(actor, ENTITY, run.getId(), "created", "primary run for " + month);
        afterCommit(() -> computation.launch(run.getId(), actor, false));
        return view(run, names(run), null);
    }

    /** Computes a draft again. Refused while it is computing, unless the heartbeat went stale. */
    @Transactional
    public RunView recompute(UUID actor, UUID runId) {
        PayRun run = locked(runId);
        if (run.getStatus() != PayRun.Status.DRAFT) {
            throw new ConflictException("Only a draft run can be computed again; this run is " + statusName(run.getStatus()) + ".");
        }
        Instant now = clock.instant();
        if (isComputing(run, now)) {
            throw new ConflictException("The run is being computed now. Wait for it to finish.");
        }
        run.setComputeState(PayRun.ComputeState.QUEUED, now);
        run.setProgress(0, run.getProgressTotal(), now);
        changes.recordLifecycle(actor, ENTITY, run.getId(), "recomputed", "compute again requested");
        afterCommit(() -> computation.launch(run.getId(), actor, true));
        return view(run, names(run), null);
    }

    /** Cancels a draft with a reason; the run, its lines and its events are kept, and a new run may be started. */
    @Transactional
    public RunView cancel(UUID actor, UUID runId, String reason) {
        String why = RunInput.requiredReason(reason, "cancelling a pay run");
        PayRun run = locked(runId);
        if (run.getStatus() != PayRun.Status.DRAFT) {
            throw new ConflictException("Only a draft run can be cancelled; this run is " + statusName(run.getStatus()) + ".");
        }
        Instant now = clock.instant();
        run.cancel(actor, why, now);
        events.save(new RunEvent(run.getId(), now, actor, "CANCELLED", why, null));
        changes.record(actor, ENTITY, run.getId(), "status", PayRun.Status.DRAFT, PayRun.Status.CANCELLED);
        changes.recordLifecycle(actor, ENTITY, run.getId(), "cancelled", why);
        return view(run, names(run), null);
    }

    // -------------------------------------------------------------------- reads

    @Transactional(readOnly = true)
    public Page<RunView> list(String monthText, String statusText, int page, int size) {
        PayRun.Status status = statusText == null || statusText.isBlank() ? null : parse(PayRun.Status.class, statusText, "status");
        PageRequest paging = PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, MAX_PAGE_SIZE)));
        Page<PayRun> found;
        if (monthText != null && !monthText.isBlank()) {
            YearMonth month = RunInput.month(monthText);
            List<PayRun> all = runs.findByMonthOrderByKindAscSequenceAsc(month.atDay(1)).stream()
                    .filter(r -> status == null || r.getStatus() == status)
                    .toList();
            int from = Math.min((int) paging.getOffset(), all.size());
            int to = Math.min(from + paging.getPageSize(), all.size());
            found = new org.springframework.data.domain.PageImpl<>(all.subList(from, to), paging, all.size());
        } else {
            found = runs.search(status, paging);
        }
        Map<UUID, String> names = names(found.getContent().stream().flatMap(r -> actors(r).stream()).collect(Collectors.toSet()));
        return found.map(r -> view(r, names, null));
    }

    @Transactional(readOnly = true)
    public RunView get(UUID runId) {
        PayRun run = runs.findById(runId).orElseThrow(() -> new NotFoundException("Pay run not found."));
        List<RunEvent> history = events.findByRunIdOrderByAtAsc(runId);
        Set<UUID> ids = new HashSet<>(actors(run));
        history.forEach(e -> ids.add(e.getActor()));
        Map<UUID, String> names = names(ids);
        return view(run, names, history);
    }

    @Transactional(readOnly = true)
    public Page<LineView> lines(UUID runId, String stateText, String kindText, String query, int page, int size) {
        if (!runs.existsById(runId)) {
            throw new NotFoundException("Pay run not found.");
        }
        Set<PersonResult.State> states = stateText == null || stateText.isBlank()
                ? EnumSet.allOf(PersonResult.State.class)
                : EnumSet.of(parse(PersonResult.State.class, stateText, "state"));
        Set<Kind> kinds = kindText == null || kindText.isBlank()
                ? EnumSet.allOf(Kind.class)
                : EnumSet.of(parse(Kind.class, kindText, "kind"));
        String like = query == null || query.isBlank() ? "%" : "%" + query.trim().toLowerCase(Locale.ROOT) + "%";
        PageRequest paging = PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, MAX_PAGE_SIZE)));
        return lines.search(runId, states, kinds, like, paging).map(l -> lineView(l, false, List.of()));
    }

    @Transactional(readOnly = true)
    public LineView line(UUID runId, UUID lineId) {
        PayRunLine line = lines.findById(lineId)
                .filter(l -> l.getRunId().equals(runId))
                .orElseThrow(() -> new NotFoundException("Pay run line not found."));
        List<AdjustmentLine> adjustments = lineAdjustments.findByLineId(lineId).stream()
                .map(a -> new AdjustmentLine(a.getAdjustmentId(), a.getKind(), money(a.getAmount()), a.getReason()))
                .toList();
        return lineView(line, true, adjustments);
    }

    // ------------------------------------------------------------------ helpers

    private PayRun locked(UUID runId) {
        return runs.lockById(runId).orElseThrow(() -> new NotFoundException("Pay run not found."));
    }

    static boolean isComputing(PayRun run, Instant now) {
        if (run.getComputeState() != PayRun.ComputeState.QUEUED && run.getComputeState() != PayRun.ComputeState.RUNNING) {
            return false;
        }
        Instant last = run.getHeartbeatAt() != null ? run.getHeartbeatAt() : run.getStartedAt();
        return last.isAfter(now.minus(RunComputation.STALE_AFTER));
    }

    /** The computation starts only once the run row is committed, so it never reads a run that does not exist yet. */
    private static void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }

    private static String statusName(PayRun.Status status) {
        return switch (status) {
            case DRAFT -> "a draft";
            case AWAITING_APPROVAL -> "awaiting approval";
            case APPROVED -> "approved";
            case CANCELLED -> "cancelled";
        };
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String raw, String label) {
        try {
            return Enum.valueOf(type, raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new InvalidInputException("Unknown " + label + " \"" + raw.trim() + "\".");
        }
    }

    private static Set<UUID> actors(PayRun r) {
        Set<UUID> ids = new HashSet<>();
        for (UUID id : new UUID[] {
            r.getStartedBy(), r.getSubmittedBy(), r.getApprovedBy(), r.getCancelledBy()
        }) {
            if (id != null) {
                ids.add(id);
            }
        }
        return ids;
    }

    private Map<UUID, String> names(PayRun run) {
        return names(actors(run));
    }

    private Map<UUID, String> names(Set<UUID> ids) {
        Map<UUID, String> map = new HashMap<>();
        if (!ids.isEmpty()) {
            for (AppUser user : users.findAllById(ids)) {
                map.put(user.getId(), user.getDisplayName());
            }
        }
        return map;
    }

    private static Person person(UUID id, Map<UUID, String> names) {
        return id == null ? null : new Person(id, names.getOrDefault(id, "Unknown user"));
    }

    private RunView view(PayRun r, Map<UUID, String> names, List<RunEvent> history) {
        Instant now = clock.instant();
        boolean stalled = (r.getComputeState() == PayRun.ComputeState.RUNNING || r.getComputeState() == PayRun.ComputeState.QUEUED)
                && !isComputing(r, now);
        // payment entries arrive with spec 013b US7; until then nothing of an approved run is recorded as paid
        boolean approved = r.getStatus() == PayRun.Status.APPROVED;
        List<EventView> eventViews = history == null
                ? null
                : history.stream()
                        .map(e -> new EventView(e.getAt(), person(e.getActor(), names), e.getKind(), e.getReason(), e.getDetail()))
                        .toList();
        return new RunView(
                r.getId(),
                r.getMonth().toString(),
                r.getKind(),
                r.getSequence(),
                r.getStatus(),
                r.getComputeState(),
                stalled,
                r.getProgressDone(),
                r.getProgressTotal(),
                new Totals(
                        money(r.getTotalEarned()),
                        money(r.getTotalDeduction()),
                        money(r.getTotalAdjustments()),
                        money(r.getTotalNet())),
                r.getPayableCount(),
                r.getCannotPayCount(),
                0,
                approved ? r.getPayableCount() : 0,
                "0.00",
                approved ? money(r.getTotalNet()) : "0.00",
                person(r.getStartedBy(), names),
                r.getStartedAt(),
                person(r.getSubmittedBy(), names),
                r.getSubmittedAt(),
                person(r.getApprovedBy(), names),
                r.getApprovedAt(),
                person(r.getCancelledBy(), names),
                r.getCancelledAt(),
                r.getCancelReason(),
                r.getPolicy(),
                r.getVersion(),
                eventViews,
                history == null ? null : r.getRecomputeSummary());
    }

    private static LineView lineView(PayRunLine l, boolean withWorking, List<AdjustmentLine> adjustments) {
        return new LineView(
                l.getId(),
                l.getPersonKind(),
                l.getPersonId(),
                l.getPersonName(),
                l.getPersonLabel(),
                l.getState(),
                l.getReason(),
                l.getPayableDays(),
                plain(l.getWorkingDays()),
                plain(l.getUnpaidDays()),
                money(l.getSalary()),
                money(l.getEarned()),
                money(l.getDeduction()),
                money(l.getAdjustments()),
                money(l.getNet()),
                l.getWarning(),
                l.getComputedAt(),
                withWorking ? l.getWorking() : null,
                withWorking ? adjustments : null);
    }

    static String money(BigDecimal value) {
        return value == null ? null : value.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    private static String plain(BigDecimal value) {
        return value == null ? null : value.stripTrailingZeros().toPlainString();
    }
}
