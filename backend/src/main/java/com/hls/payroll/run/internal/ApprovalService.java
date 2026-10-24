package com.hls.payroll.run.internal;

import com.hls.attendance.api.WorkingCalendar;
import com.hls.files.api.FileStore;
import com.hls.payroll.api.PayrollAdjustmentApplied;
import com.hls.payroll.api.PayrollRunApproved;
import com.hls.payroll.run.internal.RunViews.RunView;
import com.hls.school.api.ChangeRecorder;
import com.hls.school.api.ConflictException;
import com.hls.school.api.ForbiddenFieldException;
import com.hls.school.api.NotFoundException;
import java.sql.Date;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Approves a pay run, all or nothing (spec 013b US2, research 8). Three steps: (1) under a row lock check that the run
 * is awaiting approval and the caller is not the person who submitted it; (2) draw every payslip from the stored lines
 * on virtual threads, outside any transaction, so the lock is not held while CPU work runs; (3) in one short
 * transaction lock the run again, check it did not change, store the PDFs, record who is paid for the month, mark the
 * adjustments applied and set the run approved. If anything fails nothing is approved and the run stays awaiting.
 *
 * <p>Not itself transactional: it opens its own transactions around steps 1 and 3.
 */
@Service
public class ApprovalService {

    static final String ENTITY = "PAYROLL_RUN";
    static final String PAYSLIP_OWNER = "PAYSLIP";

    /** What step 1 hands to step 2: the run as it stood, and the paid lines to draw. */
    private record Snapshot(long version, UUID submittedBy, List<Slip> slips) {}

    private record Slip(UUID lineId, PayslipRenderer.Data data, String fileName) {}

    private final PayRunRepository runs;
    private final PayRunLineRepository lines;
    private final LineAdjustmentRepository lineAdjustments;
    private final PayAdjustmentRepository adjustments;
    private final PayslipRepository payslips;
    private final RunEventRepository events;
    private final FileStore files;
    private final ChangeRecorder changes;
    private final WorkingCalendar calendar;
    private final RunService runService;
    private final ApplicationEventPublisher publisher;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final Clock clock;

    ApprovalService(
            PayRunRepository runs,
            PayRunLineRepository lines,
            LineAdjustmentRepository lineAdjustments,
            PayAdjustmentRepository adjustments,
            PayslipRepository payslips,
            RunEventRepository events,
            FileStore files,
            ChangeRecorder changes,
            WorkingCalendar calendar,
            RunService runService,
            ApplicationEventPublisher publisher,
            JdbcTemplate jdbc,
            PlatformTransactionManager transactions,
            Clock clock) {
        this.runs = runs;
        this.lines = lines;
        this.lineAdjustments = lineAdjustments;
        this.adjustments = adjustments;
        this.payslips = payslips;
        this.events = events;
        this.files = files;
        this.changes = changes;
        this.calendar = calendar;
        this.runService = runService;
        this.publisher = publisher;
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(transactions);
        this.clock = clock;
    }

    public RunView approve(UUID actor, UUID runId) {
        LocalDate approvedOn = calendar.businessToday();
        Snapshot snapshot = tx.execute(s -> snapshot(actor, runId, approvedOn));
        Map<UUID, byte[]> pdfs = render(snapshot.slips());
        tx.executeWithoutResult(s -> commit(actor, runId, snapshot, pdfs));
        return runService.get(runId);
    }

    // ------------------------------------------------------------------ step 1

    private Snapshot snapshot(UUID actor, UUID runId, LocalDate approvedOn) {
        PayRun run = runs.lockById(runId).orElseThrow(() -> new NotFoundException("Pay run not found."));
        check(run, actor);
        String runName = run.getKind() == PayRun.Kind.PRIMARY
                ? "Primary run"
                : "Supplementary run " + run.getSequence();
        String monthName = RunInput.monthName(run.getMonth());
        List<Slip> slips = new ArrayList<>();
        for (PayRunLine line : lines.findByRunIdAndState(runId, PersonResult.State.PAYABLE)) {
            PayslipRenderer.Data data = new PayslipRenderer.Data(
                    monthName,
                    runName,
                    line.getPersonName(),
                    line.getPersonLabel(),
                    line.getPayableDays(),
                    line.getWorkingDays(),
                    line.getUnpaidDays(),
                    line.getEarned(),
                    line.getDeduction(),
                    line.getAdjustments(),
                    line.getNet(),
                    line.getWorking(),
                    approvedOn);
            slips.add(new Slip(line.getId(), data, fileName(line.getPersonName(), run)));
        }
        return new Snapshot(run.getVersion(), run.getSubmittedBy(), slips);
    }

    /** The approval rules: the run waits for approval, and the caller did not submit it (FR-014). */
    private static void check(PayRun run, UUID actor) {
        if (run.getStatus() != PayRun.Status.AWAITING_APPROVAL) {
            throw new ConflictException("Only a run awaiting approval can be approved; this run is "
                    + run.getStatus().name().toLowerCase().replace('_', ' ') + ".");
        }
        if (actor.equals(run.getSubmittedBy())) {
            throw new ForbiddenFieldException("You submitted this run, so someone else must approve it.");
        }
    }

    private static String fileName(String person, PayRun run) {
        String safe = person.replaceAll("[^A-Za-z0-9]+", "-").replaceAll("^-|-$", "");
        return "Payslip-" + (safe.isEmpty() ? "person" : safe) + "-" + run.getMonth() + ".pdf";
    }

    // ------------------------------------------------------------------ step 2

    private Map<UUID, byte[]> render(List<Slip> slips) {
        Map<UUID, byte[]> pdfs = new HashMap<>();
        Semaphore cpus = new Semaphore(Math.max(1, Runtime.getRuntime().availableProcessors()));
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Map<UUID, Future<byte[]>> pending = new HashMap<>();
            for (Slip slip : slips) {
                pending.put(slip.lineId(), executor.submit(() -> {
                    cpus.acquire();
                    try {
                        return PayslipRenderer.render(slip.data());
                    } finally {
                        cpus.release();
                    }
                }));
            }
            for (Map.Entry<UUID, Future<byte[]>> entry : pending.entrySet()) {
                pdfs.put(entry.getKey(), entry.getValue().get());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("The payslips could not be drawn", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("A payslip could not be drawn; nothing was approved.", e.getCause());
        }
        return pdfs;
    }

    // ------------------------------------------------------------------ step 3

    private void commit(UUID actor, UUID runId, Snapshot snapshot, Map<UUID, byte[]> pdfs) {
        PayRun run = runs.lockById(runId).orElseThrow(() -> new NotFoundException("Pay run not found."));
        check(run, actor);
        if (run.getVersion() != snapshot.version()) {
            throw new ConflictException("The run changed while it was being approved. Check it and approve again.");
        }
        Instant now = clock.instant();
        Date month = Date.valueOf(run.getMonth().atDay(1));

        for (Slip slip : snapshot.slips()) {
            FileStore.FileRef file = files.store(actor, PAYSLIP_OWNER, slip.lineId(), slip.fileName(), pdfs.get(slip.lineId()));
            payslips.save(new Payslip(slip.lineId(), runId, file.id(), now));
        }
        recordPaidMonth(run, month, snapshot);
        markAdjustmentsApplied(run, snapshot);

        run.approve(actor, now);
        runs.flush();
        events.save(new RunEvent(runId, now, actor, "APPROVED", null, null));
        changes.record(actor, ENTITY, runId, "status", PayRun.Status.AWAITING_APPROVAL, PayRun.Status.APPROVED);
        publisher.publishEvent(new PayrollRunApproved(runId, run.getMonth(), actor, run.getSubmittedBy()));
    }

    /** Nobody is paid twice for a month: the key of payroll_paid_month refuses a second approved run (SC-007). */
    private void recordPaidMonth(PayRun run, Date month, Snapshot snapshot) {
        try {
            jdbc.batchUpdate(
                    "insert into payroll_paid_month (month, person_kind, person_id, run_id, line_id) values (?, ?, ?, ?, ?)",
                    lines.findByRunIdAndState(run.getId(), PersonResult.State.PAYABLE).stream()
                            .map(line -> new Object[] {
                                month, line.getPersonKind().name(), line.getPersonId(), run.getId(), line.getId()
                            })
                            .collect(Collectors.toList()));
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("Someone in this run has already been paid for "
                    + RunInput.monthName(run.getMonth()) + " by another approved run. Nothing was approved.");
        }
    }

    private void markAdjustmentsApplied(PayRun run, Snapshot snapshot) {
        UUID runId = run.getId();
        List<UUID> lineIds = snapshot.slips().stream().map(Slip::lineId).toList();
        if (lineIds.isEmpty()) {
            return;
        }
        List<LineAdjustment> copies = lineAdjustments.findByLineIdIn(lineIds);
        Map<UUID, PayAdjustment> byId = adjustments.findAllById(
                        copies.stream().map(LineAdjustment::getAdjustmentId).toList())
                .stream()
                .collect(Collectors.toMap(PayAdjustment::getId, a -> a));
        for (LineAdjustment copy : copies) {
            PayAdjustment adjustment = byId.get(copy.getAdjustmentId());
            adjustment.apply(runId, copy.getLineId());
            if (adjustment.getSourceRef() != null) {
                // spec 015: the module that asked for the adjustment settles its own record in this same transaction
                publisher.publishEvent(new PayrollAdjustmentApplied(
                        adjustment.getSourceRef(), adjustment.getKind().name(), runId, run.getMonth().atDay(1)));
            }
        }
    }
}
