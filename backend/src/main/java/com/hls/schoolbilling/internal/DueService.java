package com.hls.schoolbilling.internal;

import com.hls.designation.api.BusinessDate;
import com.hls.school.api.ChangeRecorder;
import com.hls.school.api.ConflictException;
import com.hls.school.api.InvalidInputException;
import com.hls.school.api.NotFoundException;
import com.hls.school.api.SchoolDirectory.SchoolInfo;
import com.hls.schoolbilling.api.MonthlyDueRaised;
import com.hls.schoolbilling.internal.ReceivablesDtos.BillingCaller;
import com.hls.schoolbilling.internal.ReceivablesDtos.DueRow;
import com.hls.schoolbilling.internal.ReceivablesDtos.NotBilledRow;
import com.hls.schoolbilling.internal.ReceivablesDtos.RaiseResult;
import com.hls.schoolbilling.internal.ReceivablesDtos.Skipped;
import com.hls.schoolbilling.internal.ReceivablesStore.DueRecord;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/** Raises and cancels monthly dues (spec 022 US1 and US2). A raised due never changes. */
@Service
public class DueService {

    static final String ENTITY = "MONTHLY_DUE";
    static final int REASON_MAX = 300;

    private final ReceivablesStore store;
    private final DueCalculator calculator;
    private final BillingSettings settings;
    private final BillingScope scope;
    private final LedgerQueryService queries;
    private final OverdueFollowUps followUps;
    private final ChangeRecorder changes;
    private final ApplicationEventPublisher events;
    private final BusinessDate businessDate;
    private final Clock clock;
    private final TransactionTemplate tx;

    DueService(
            ReceivablesStore store,
            DueCalculator calculator,
            BillingSettings settings,
            BillingScope scope,
            LedgerQueryService queries,
            OverdueFollowUps followUps,
            ChangeRecorder changes,
            ApplicationEventPublisher events,
            BusinessDate businessDate,
            Clock clock,
            PlatformTransactionManager transactions) {
        this.store = store;
        this.calculator = calculator;
        this.settings = settings;
        this.scope = scope;
        this.queries = queries;
        this.followUps = followUps;
        this.changes = changes;
        this.events = events;
        this.businessDate = businessDate;
        this.clock = clock;
        this.tx = new TransactionTemplate(transactions);
    }

    /** Raises one School's due for the month. 409 when a non-cancelled due exists; 400 when there is nothing to bill. */
    @Transactional
    public DueRow raise(BillingCaller caller, UUID schoolId, YearMonth month) {
        SchoolInfo school = scope.requireSchool(caller, schoolId);
        LocalDate today = businessDate.today();
        if (month == null) {
            throw new InvalidInputException("The month is required.");
        }
        if (month.atDay(1).isAfter(today)) {
            throw new InvalidInputException("A due can only be raised for a month that has started.");
        }
        store.lockSchool(schoolId);
        Optional<DueRecord> existing = store.liveDue(schoolId, month);
        if (existing.isPresent()) {
            throw new ConflictException("A monthly due for " + school.name() + " and " + month + " already exists ("
                    + existing.get().reference() + ").");
        }
        DueCalculator.Result result = calculator.compute(schoolId, month);
        if (result.lines().isEmpty() || result.total().signum() <= 0) {
            throw new InvalidInputException("Nothing to bill for " + school.name() + " in " + month + ".");
        }
        UUID id = UUID.randomUUID();
        BigDecimal total = result.total();
        store.insertDue(id, store.nextReference(month), schoolId, month, today, today.plusDays(settings.paymentDueDays()), total,
                caller.userId(), clock.instant());
        result.lines().forEach(l -> store.insertLine(id, l));
        changes.recordLifecycle(caller.userId(), ENTITY, id, "created", school.name() + " " + month + " total " + total.toPlainString());
        events.publishEvent(new MonthlyDueRaised(id, schoolId, month, total));
        return queries.row(store.due(id).orElseThrow());
    }

    /** Raises the month for every given School (all in scope when none are given); each School in its own transaction. */
    public RaiseResult raiseAll(BillingCaller caller, YearMonth month, List<UUID> schoolIds) {
        boolean explicit = schoolIds != null && !schoolIds.isEmpty();
        List<SchoolInfo> targets = scope.schoolsInScope(caller).stream()
                .filter(s -> explicit ? schoolIds.contains(s.id()) : s.active())
                .toList();
        if (explicit) {
            Set<UUID> known = targets.stream().map(SchoolInfo::id).collect(Collectors.toSet());
            for (UUID id : schoolIds) {
                if (!known.contains(id)) {
                    throw new NotFoundException("School not found.");
                }
            }
        }
        List<DueRow> raised = new ArrayList<>();
        List<Skipped> skipped = new ArrayList<>();
        for (SchoolInfo school : targets) {
            try {
                DueRow row = tx.execute(status -> raise(caller, school.id(), month));
                raised.add(row);
            } catch (ConflictException e) {
                UUID existing = store.liveDue(school.id(), month).map(DueRecord::id).orElse(null);
                skipped.add(new Skipped(school.id(), school.name(), "ALREADY_RAISED", existing));
            } catch (InvalidInputException e) {
                if (explicit || !e.getMessage().startsWith("Nothing to bill")) {
                    skipped.add(new Skipped(school.id(), school.name(), explicit ? "NOTHING_TO_BILL" : "INVALID", null));
                }
            }
        }
        return new RaiseResult(raised, skipped, notBilled(caller, month, targets));
    }

    private List<NotBilledRow> notBilled(BillingCaller caller, YearMonth month, List<SchoolInfo> targets) {
        Set<UUID> ids = targets.stream().map(SchoolInfo::id).collect(Collectors.toSet());
        return queries.notBilled(caller, month).stream().filter(r -> ids.contains(r.schoolId())).toList();
    }

    /** Cancels a due with a reason; refused while collections are allocated to it. */
    @Transactional
    public DueRow cancel(BillingCaller caller, UUID dueId, String reason) {
        DueRecord due = store.due(dueId).orElseThrow(() -> new NotFoundException("Monthly due not found."));
        scope.requireSchool(caller, due.schoolId());
        String text = reason == null ? "" : reason.trim();
        if (text.length() < 3 || text.length() > REASON_MAX) {
            throw new InvalidInputException("A reason of 3 to " + REASON_MAX + " characters is required.");
        }
        store.lockSchool(due.schoolId());
        due = store.due(dueId).orElseThrow();
        if (due.cancelled()) {
            throw new ConflictException("This monthly due is already cancelled.");
        }
        if (store.allocationsOnDue(dueId).signum() != 0) {
            throw new ConflictException("Collections are allocated to this monthly due. Reverse them first.");
        }
        store.cancelDue(dueId, caller.userId(), text, clock.instant());
        changes.record(caller.userId(), ENTITY, dueId, "status", LedgerQueryService.statusOf(due), "CANCELLED");
        changes.record(caller.userId(), ENTITY, dueId, "cancelReason", null, text);
        followUps.closeIfSettled(dueId, caller.userId());
        return queries.row(store.due(dueId).orElseThrow());
    }
}
