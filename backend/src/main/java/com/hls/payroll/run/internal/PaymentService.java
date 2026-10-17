package com.hls.payroll.run.internal;

import com.hls.attendance.api.WorkingCalendar;
import com.hls.identity.user.AppUser;
import com.hls.identity.user.AppUserRepository;
import com.hls.school.api.ChangeRecorder;
import com.hls.school.api.ConflictException;
import com.hls.school.api.InvalidInputException;
import com.hls.school.api.NotFoundException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Manual payment records of an approved run (spec 013b US7). Payment happens outside the system: this only records
 * whether a line was paid, when, how and with which reference. Entries are only added, so a correction keeps the
 * earlier ones, and no amount of the run ever changes.
 */
@Service
public class PaymentService {

    static final String ENTITY = "PAYROLL_PAYMENT";

    /** One entry; the amounts of the line are never part of it. */
    public record EntryView(
            UUID id,
            boolean paid,
            LocalDate paidOn,
            PaymentEntry.Mode mode,
            String reference,
            UUID enteredBy,
            String enteredByName,
            Instant enteredAt) {}

    /** The current entry of a line, and the earlier ones newest first. */
    public record PaymentView(UUID lineId, EntryView current, List<EntryView> earlier) {}

    /** How much of an approved run is paid, from the newest entry of each payable line. */
    public record Figures(int paidCount, int unpaidCount, BigDecimal paidAmount, BigDecimal unpaidAmount) {}

    private final PayRunRepository runs;
    private final PayRunLineRepository lines;
    private final PaymentEntryRepository entries;
    private final ChangeRecorder changes;
    private final WorkingCalendar calendar;
    private final AppUserRepository users;
    private final Clock clock;

    PaymentService(
            PayRunRepository runs,
            PayRunLineRepository lines,
            PaymentEntryRepository entries,
            ChangeRecorder changes,
            WorkingCalendar calendar,
            AppUserRepository users,
            Clock clock) {
        this.runs = runs;
        this.lines = lines;
        this.entries = entries;
        this.changes = changes;
        this.calendar = calendar;
        this.users = users;
        this.clock = clock;
    }

    /** Adds an entry for a payable line of an approved run; the newest entry is what the line shows. */
    @Transactional
    public PaymentView record(
            UUID actor, UUID runId, UUID lineId, Boolean paid, LocalDate paidOn, String modeText, String reference) {
        PayRun run = runs.lockById(runId).orElseThrow(() -> new NotFoundException("Pay run not found."));
        PayRunLine line = lines.findById(lineId)
                .filter(l -> l.getRunId().equals(runId))
                .orElseThrow(() -> new NotFoundException("Pay run line not found."));
        if (run.getStatus() != PayRun.Status.APPROVED) {
            throw new ConflictException("Payment can be recorded only on an approved run; this run is not approved.");
        }
        if (line.getState() != PersonResult.State.PAYABLE) {
            throw new ConflictException("This person cannot be paid in this run, so no payment can be recorded.");
        }
        if (paid == null) {
            throw new InvalidInputException("The field paid is required.");
        }
        PaymentEntry entry;
        Instant now = clock.instant();
        if (paid) {
            LocalDate date = RunInput.paymentDate(paidOn, calendar.businessToday());
            entry = new PaymentEntry(lineId, true, date, mode(modeText), RunInput.reference(reference), actor, now);
        } else {
            // "not paid" carries no details; the earlier entries stay in the history
            entry = new PaymentEntry(lineId, false, null, null, null, actor, now);
        }
        List<PaymentEntry> before = entries.findByLineIdOrderByEnteredAtDesc(lineId);
        entries.save(entry);
        changes.recordLifecycle(actor, ENTITY, lineId, before.isEmpty() ? "recorded" : "corrected", describe(entry));
        return view(lineId, history(lineId));
    }

    @Transactional(readOnly = true)
    public PaymentView get(UUID runId, UUID lineId) {
        if (!runs.existsById(runId)) {
            throw new NotFoundException("Pay run not found.");
        }
        lines.findById(lineId).filter(l -> l.getRunId().equals(runId))
                .orElseThrow(() -> new NotFoundException("Pay run line not found."));
        return view(lineId, history(lineId));
    }

    /** The newest entry of each of the lines, for the lists; a line with none is missing from the map. */
    @Transactional(readOnly = true)
    public Map<UUID, EntryView> currentOf(Collection<UUID> lineIds) {
        Map<UUID, PaymentEntry> newest = newest(lineIds);
        Map<UUID, String> names = names(newest.values());
        Map<UUID, EntryView> out = new HashMap<>();
        newest.forEach((id, e) -> out.put(id, entryView(e, names)));
        return out;
    }

    /** Paid and unpaid counts and amounts of an approved run; all zero for a run that is not approved. */
    @Transactional(readOnly = true)
    public Figures figures(PayRun run) {
        if (run.getStatus() != PayRun.Status.APPROVED) {
            return new Figures(0, 0, BigDecimal.ZERO, BigDecimal.ZERO);
        }
        List<PayRunLine> payable = lines.findByRunIdAndState(run.getId(), PersonResult.State.PAYABLE);
        Map<UUID, PaymentEntry> newest = newest(payable.stream().map(PayRunLine::getId).toList());
        int paidCount = 0;
        int unpaidCount = 0;
        BigDecimal paidAmount = BigDecimal.ZERO;
        BigDecimal unpaidAmount = BigDecimal.ZERO;
        for (PayRunLine line : payable) {
            BigDecimal net = line.getNet() == null ? BigDecimal.ZERO : line.getNet();
            PaymentEntry latest = newest.get(line.getId());
            if (latest != null && latest.isPaid()) {
                paidCount++;
                paidAmount = paidAmount.add(net);
            } else {
                unpaidCount++;
                unpaidAmount = unpaidAmount.add(net);
            }
        }
        return new Figures(paidCount, unpaidCount, paidAmount, unpaidAmount);
    }

    // ------------------------------------------------------------------ helpers

    private static PaymentEntry.Mode mode(String text) {
        if (text == null || text.isBlank()) {
            throw new InvalidInputException("The payment mode is required.");
        }
        try {
            return PaymentEntry.Mode.valueOf(text.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new InvalidInputException(
                    "The payment mode must be one of BANK_TRANSFER, CASH, UPI, CHEQUE or OTHER.");
        }
    }

    private static String describe(PaymentEntry e) {
        return e.isPaid()
                ? "paid on " + RunInput.date(e.getPaidOn()) + " by " + e.getMode()
                        + (e.getReference() == null ? "" : ", reference " + e.getReference())
                : "not paid";
    }

    private List<PaymentEntry> history(UUID lineId) {
        return entries.findByLineIdOrderByEnteredAtDesc(lineId);
    }

    private Map<UUID, PaymentEntry> newest(Collection<UUID> lineIds) {
        Map<UUID, PaymentEntry> newest = new HashMap<>();
        if (lineIds.isEmpty()) {
            return newest;
        }
        // newest first, so the first entry seen for a line is its current one
        for (PaymentEntry e : entries.findByLineIdInOrderByEnteredAtDesc(lineIds)) {
            newest.putIfAbsent(e.getLineId(), e);
        }
        return newest;
    }

    private Map<UUID, String> names(Collection<PaymentEntry> found) {
        var ids = found.stream().map(PaymentEntry::getEnteredBy).collect(Collectors.toSet());
        Map<UUID, String> names = new HashMap<>();
        if (!ids.isEmpty()) {
            for (AppUser user : users.findAllById(ids)) {
                names.put(user.getId(), user.getDisplayName());
            }
        }
        return names;
    }

    private static EntryView entryView(PaymentEntry e, Map<UUID, String> names) {
        return new EntryView(
                e.getId(),
                e.isPaid(),
                e.getPaidOn(),
                e.getMode(),
                e.getReference(),
                e.getEnteredBy(),
                names.getOrDefault(e.getEnteredBy(), "Unknown user"),
                e.getEnteredAt());
    }

    private PaymentView view(UUID lineId, List<PaymentEntry> newestFirst) {
        Map<UUID, String> names = names(newestFirst);
        List<EntryView> all = new ArrayList<>();
        newestFirst.forEach(e -> all.add(entryView(e, names)));
        return new PaymentView(lineId, all.isEmpty() ? null : all.get(0), all.isEmpty() ? List.of() : all.subList(1, all.size()));
    }
}
