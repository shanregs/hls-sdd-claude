package com.hls.attendance.manager.internal;

import com.hls.attendance.api.ManagerMonth;
import com.hls.attendance.api.PendingManagerRequests;
import com.hls.attendance.api.PendingManagerRequests.Pending;
import com.hls.attendance.internal.BusinessCalendar;
import com.hls.attendance.manager.internal.ManagerMonthEvent.Event;
import com.hls.attendance.manager.internal.ManagerMonthState.Frozen;
import com.hls.attendance.manager.internal.ManagerRegularization.Status;
import com.hls.organization.api.ManagerQueries;
import com.hls.organization.api.ManagerQueries.ManagerEmployment;
import com.hls.organization.api.ManagerQueries.ManagerRef;
import com.hls.school.api.ConflictException;
import com.hls.school.api.InvalidInputException;
import com.hls.school.api.NotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Locks a Manager's month for payroll and reopens it (spec 032 US6). A month can be locked once it has ended and
 * nothing touching it is waiting for a decision; locking freezes the figures payroll reads. Every writer refuses a
 * locked month through {@link ManagerMonthGuard}; locking takes the same per-day locks the writers take, so a write
 * and a lock cannot both slip through. Locking all Managers locks each that can be locked and says why for the rest.
 */
@Service
public class ManagerMonthLockService {

    public static final String FORGOTTEN_CHECK_IN = "FORGOTTEN_CHECK_IN";
    private static final int MAX_REASON = 500;

    /** A waiting request with its Manager's name, for the refusal. */
    public record PendingView(
            String kind, UUID requestId, UUID managerId, String managerName, LocalDate firstDate, LocalDate lastDate) {}

    public record Locked(UUID managerId, String name) {}

    public record Refusal(UUID managerId, String name, String reason, List<PendingView> pending) {}

    public record LockResult(String month, List<Locked> locked, List<Refusal> refused, int alreadyLocked) {}

    public record ReopenResult(String month, UUID managerId, String name, boolean locked) {}

    private final ManagerQueries managers;
    private final ManagerAttendanceReadApiImpl readApi;
    private final ManagerMonthStateRepository states;
    private final ManagerMonthEventRepository events;
    private final ManagerRegularizationRepository regularizations;
    private final ObjectProvider<PendingManagerRequests> pendingSources;
    private final ManagerAttendanceAudit audit;
    private final BusinessCalendar business;
    private final Clock clock;
    private final JdbcTemplate jdbc;

    public ManagerMonthLockService(
            ManagerQueries managers,
            ManagerAttendanceReadApiImpl readApi,
            ManagerMonthStateRepository states,
            ManagerMonthEventRepository events,
            ManagerRegularizationRepository regularizations,
            ObjectProvider<PendingManagerRequests> pendingSources,
            ManagerAttendanceAudit audit,
            BusinessCalendar business,
            Clock clock,
            JdbcTemplate jdbc) {
        this.managers = managers;
        this.readApi = readApi;
        this.states = states;
        this.events = events;
        this.regularizations = regularizations;
        this.pendingSources = pendingSources;
        this.audit = audit;
        this.business = business;
        this.clock = clock;
        this.jdbc = jdbc;
    }

    /** Locks one Manager's month ({@code managerId}) or every lockable Manager's month (null). */
    @Transactional
    public LockResult lock(UUID actor, YearMonth month, UUID managerId) {
        if (!month.atEndOfMonth().isBefore(business.today())) {
            throw new ConflictException("The month " + month + " has not ended yet, so it cannot be locked.");
        }
        List<ManagerRef> all = managers.allManagers();
        List<ManagerRef> asked = managerId == null
                ? all
                : List.of(all.stream()
                        .filter(m -> m.id().equals(managerId))
                        .findFirst()
                        .orElseThrow(() -> new NotFoundException("Manager not found.")));
        Map<UUID, ManagerEmployment> employment =
                managers.employment(asked.stream().map(ManagerRef::id).toList());
        Map<UUID, String> names = all.stream().collect(Collectors.toMap(ManagerRef::id, ManagerRef::displayName, (a, b) -> a));
        List<Pending> waiting = allPending(month);

        List<Locked> locked = new ArrayList<>();
        List<Refusal> refused = new ArrayList<>();
        int already = 0;
        for (ManagerRef ref : asked.stream().sorted(Comparator.comparing(m -> m.displayName().toLowerCase(Locale.ROOT))).toList()) {
            ManagerEmployment emp = employment.get(ref.id());
            boolean noJoining = emp == null || emp.joiningDate() == null;
            if (managerId == null && !noJoining && !employedIn(emp, month)) {
                continue; // not employed at any point in the month: nothing to lock
            }
            lockDays(ref.id(), month);
            ManagerMonthState state = states.findById(new ManagerMonthState.Key(ref.id(), month))
                    .orElseGet(() -> new ManagerMonthState(ref.id(), month));
            if (state.isLocked()) {
                if (managerId != null) {
                    throw new ConflictException(ref.displayName() + "'s month " + month + " is already locked.");
                }
                already++;
                continue;
            }
            if (noJoining) {
                refused.add(new Refusal(
                        ref.id(), ref.displayName(), "The joining date has not been recorded, so the figures cannot be frozen.", List.of()));
                continue;
            }
            List<PendingView> pending = waiting.stream()
                    .filter(p -> p.managerId().equals(ref.id()))
                    .map(p -> view(p, names))
                    .toList();
            if (!pending.isEmpty()) {
                refused.add(new Refusal(
                        ref.id(),
                        ref.displayName(),
                        pending.size() + " request" + (pending.size() == 1 ? " is" : "s are")
                                + " still waiting for a decision. Decide them first.",
                        pending));
                continue;
            }
            ManagerMonth figures = readApi.month(ref.id(), month);
            if (!(figures instanceof ManagerMonth.Found found)) {
                refused.add(new Refusal(ref.id(), ref.displayName(), "The month's figures cannot be worked out.", List.of()));
                continue;
            }
            Instant now = clock.instant();
            state.lock(
                    new Frozen(
                            found.workingDays(),
                            found.daysWorked(),
                            found.leavePaid(),
                            found.leaveUnpaid(),
                            found.unpaid(),
                            found.fieldDays(),
                            FrozenUnpaidDates.encode(found.unpaidDays())),
                    actor,
                    now);
            states.saveAndFlush(state);
            events.save(new ManagerMonthEvent(ref.id(), month, Event.LOCKED, null, actor, now));
            audit.changed(actor, ManagerAttendanceAudit.MONTH, ref.id(), "state", "OPEN", "LOCKED " + month);
            locked.add(new Locked(ref.id(), ref.displayName()));
        }
        return new LockResult(month.toString(), locked, refused, already);
    }

    /** Reopens one Manager's locked month with a reason; the frozen figures are cleared. */
    @Transactional
    public ReopenResult reopen(UUID actor, YearMonth month, UUID managerId, String reasonText) {
        String reason = reasonText == null ? "" : reasonText.trim();
        if (reason.isEmpty()) {
            throw new InvalidInputException("A reason is required to reopen a month.");
        }
        if (reason.length() > MAX_REASON) {
            throw new InvalidInputException("The reason can be at most " + MAX_REASON + " characters.");
        }
        ManagerRef ref = managers.allManagers().stream()
                .filter(m -> m.id().equals(managerId))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Manager not found."));
        lockDays(ref.id(), month);
        ManagerMonthState state = states.findById(new ManagerMonthState.Key(ref.id(), month))
                .filter(ManagerMonthState::isLocked)
                .orElseThrow(() -> new ConflictException(ref.displayName() + "'s month " + month + " is not locked."));
        state.reopen();
        states.saveAndFlush(state);
        events.save(new ManagerMonthEvent(ref.id(), month, Event.REOPENED, reason, actor, clock.instant()));
        audit.changed(actor, ManagerAttendanceAudit.MONTH, ref.id(), "state", "LOCKED " + month, "OPEN");
        audit.changed(actor, ManagerAttendanceAudit.MONTH, ref.id(), "reopenReason", null, reason);
        return new ReopenResult(month.toString(), ref.id(), ref.displayName(), false);
    }

    // ------------------------------------------------------------------ helpers

    /** Every request waiting for a decision that touches the month: the other modules' and attendance's own. */
    private List<Pending> allPending(YearMonth month) {
        List<Pending> out = new ArrayList<>();
        pendingSources.orderedStream().forEach(source -> out.addAll(source.pendingFor(month)));
        for (ManagerRegularization r :
                regularizations.findByStatusAndDayDateBetween(Status.PENDING, month.atDay(1), month.atEndOfMonth())) {
            out.add(new Pending(FORGOTTEN_CHECK_IN, r.getId(), r.getManagerId(), r.getDayDate(), r.getDayDate()));
        }
        return out;
    }

    private static PendingView view(Pending p, Map<UUID, String> names) {
        return new PendingView(p.kind(), p.requestId(), p.managerId(), names.get(p.managerId()), p.firstDate(), p.lastDate());
    }

    private static boolean employedIn(ManagerEmployment emp, YearMonth month) {
        return !emp.joiningDate().isAfter(month.atEndOfMonth())
                && (emp.exitDate() == null || !emp.exitDate().isBefore(month.atDay(1)));
    }

    /** Takes the per-day lock every writer takes, for each day of the month, so a write in flight finishes first. */
    private void lockDays(UUID managerId, YearMonth month) {
        jdbc.queryForList(
                "select pg_advisory_xact_lock(hashtextextended('manager-day:' || ? || ':' || d::date, 0))"
                        + " from generate_series(?::date, ?::date, interval '1 day') d",
                managerId.toString(),
                month.atDay(1).toString(),
                month.atEndOfMonth().toString());
    }
}
