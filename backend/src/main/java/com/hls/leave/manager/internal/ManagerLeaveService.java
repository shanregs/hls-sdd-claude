package com.hls.leave.manager.internal;

import com.hls.attendance.api.ManagerLeaveAttendance;
import com.hls.attendance.api.ManagerLeaveAttendance.LeaveDay;
import com.hls.leave.api.LeaveTypes;
import com.hls.leave.api.ManagerLeaveCancelled;
import com.hls.leave.api.ManagerLeaveDecided;
import com.hls.leave.api.ManagerLeaveDecided.Decision;
import com.hls.leave.api.ManagerLeaveRequested;
import com.hls.leave.internal.LeaveType;
import com.hls.leave.internal.LeaveTypeCatalog;
import com.hls.leave.manager.internal.ManagerLeaveRequest.Status;
import com.hls.organization.api.ManagerQueries;
import com.hls.organization.api.ManagerQueries.ManagerEmployment;
import com.hls.organization.api.ManagerQueries.ManagerRef;
import com.hls.school.api.ChangeRecorder;
import com.hls.school.api.ConflictException;
import com.hls.school.api.InvalidInputException;
import com.hls.school.api.NotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A Manager's leave (spec 032 US3): the Manager asks, an Admin or Director decides. Approving writes the leave days
 * onto the Manager's records through {@link ManagerLeaveAttendance}; revoking removes them. Nobody decides their own
 * request, even a user who is both a Manager and an Admin or Director.
 */
@Service
public class ManagerLeaveService {

    public static final String AUDIT_TYPE = "MANAGER_LEAVE";
    static final int MAX_TEXT = 500;

    /** The leave types a Manager can choose, with the one that is Loss-of-Pay marked. */
    public record TypeView(UUID id, String name, boolean lossOfPay) {}

    /** What the Manager sends. */
    public record RequestInput(
            String leaveTypeId, String firstDate, String lastDate, Boolean firstHalf, Boolean lastHalf, String reason) {}

    /** The request as the screens see it. */
    public record LeaveView(
            UUID id,
            UUID managerId,
            String managerName,
            UUID leaveTypeId,
            String leaveTypeName,
            boolean lossOfPay,
            LocalDate firstDate,
            LocalDate lastDate,
            boolean firstHalf,
            boolean lastHalf,
            String reason,
            String status,
            Instant requestedAt,
            UUID decidedBy,
            Instant decidedAt,
            String decisionReason) {}

    private record Who(UUID managerId, UUID userId, String name, ManagerEmployment employment) {}

    private final ManagerLeaveRequestRepository requests;
    private final ManagerQueries managers;
    private final ManagerLeaveAttendance attendance;
    private final LeaveTypeCatalog types;
    private final ChangeRecorder recorder;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public ManagerLeaveService(
            ManagerLeaveRequestRepository requests,
            ManagerQueries managers,
            ManagerLeaveAttendance attendance,
            LeaveTypeCatalog types,
            ChangeRecorder recorder,
            ApplicationEventPublisher events,
            Clock clock) {
        this.requests = requests;
        this.managers = managers;
        this.attendance = attendance;
        this.types = types;
        this.recorder = recorder;
        this.events = events;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ the Manager

    @Transactional(readOnly = true)
    public List<TypeView> activeTypes() {
        return types.active().stream()
                .map(t -> new TypeView(t.getId(), t.getName(), isLossOfPay(t)))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<LeaveView> mine(UUID userId) {
        Who who = whoIsUser(userId);
        return requests.findByManagerIdOrderByRequestedAtDesc(who.managerId()).stream()
                .map(r -> view(r, who.name()))
                .toList();
    }

    @Transactional
    public LeaveView request(UUID userId, RequestInput in) {
        Who who = whoIsUser(userId);
        LocalDate joining = who.employment() == null ? null : who.employment().joiningDate();
        if (joining == null) {
            throw new InvalidInputException(
                    "Your joining date has not been recorded yet. Ask an Admin to record it first.");
        }
        LocalDate exit = who.employment().exitDate();
        UUID typeId = parseId(in.leaveTypeId());
        LeaveType type = types.find(typeId)
                .filter(LeaveType::isActive)
                .orElseThrow(() -> new InvalidInputException("Choose a leave type."));
        LocalDate first = parseDate(in.firstDate(), "The first date must look like 2026-11-03.");
        LocalDate last = parseDate(in.lastDate(), "The last date must look like 2026-11-03.");
        String reason = text(in.reason(), "A reason is required.");
        boolean firstHalf = Boolean.TRUE.equals(in.firstHalf());
        boolean lastHalf = Boolean.TRUE.equals(in.lastHalf());
        if (last.isBefore(first)) {
            throw new InvalidInputException("The last date cannot be before the first date.");
        }
        if (first.isBefore(joining)) {
            throw new InvalidInputException("The first date is before your joining date.");
        }
        if (exit != null && last.isAfter(exit)) {
            throw new InvalidInputException("The last date is after your exit date.");
        }
        List<LocalDate> working = attendance.workingDays(who.managerId(), first, last);
        if (working.isEmpty()) {
            throw new InvalidInputException("The range has no working day, so there is nothing to take leave for.");
        }
        if (first.equals(last) && firstHalf && lastHalf) {
            throw new InvalidInputException("One day cannot be two half days. Choose one half day.");
        }
        if (firstHalf && !working.contains(first)) {
            throw new InvalidInputException("A half day must fall on a working day, and the first date is not one.");
        }
        if (lastHalf && !working.contains(last)) {
            throw new InvalidInputException("A half day must fall on a working day, and the last date is not one.");
        }
        List<ManagerLeaveRequest> overlapping =
                requests.findByManagerIdAndStatusInAndFirstDateLessThanEqualAndLastDateGreaterThanEqual(
                        who.managerId(), EnumSet.of(Status.PENDING, Status.APPROVED), last, first);
        if (!overlapping.isEmpty()) {
            throw new ConflictException("You already have a pending or approved leave request that overlaps these dates.");
        }
        ManagerLeaveRequest saved = requests.saveAndFlush(new ManagerLeaveRequest(
                who.managerId(), type.getId(), first, last, firstHalf, lastHalf, reason, clock.instant()));
        recorder.recordLifecycle(
                userId,
                AUDIT_TYPE,
                saved.getId(),
                "created",
                who.name() + " " + type.getName() + " " + first + ".." + last + " (" + reason + ")");
        events.publishEvent(new ManagerLeaveRequested(saved.getId(), who.managerId(), first, last));
        return view(saved, who.name());
    }

    @Transactional
    public void cancel(UUID userId, UUID id) {
        Who who = whoIsUser(userId);
        ManagerLeaveRequest request = requests.findById(id)
                .filter(r -> r.getManagerId().equals(who.managerId()))
                .orElseThrow(() -> new NotFoundException("That request was not found."));
        if (request.getStatus() != Status.PENDING) {
            throw new ConflictException(request.getStatus() == Status.APPROVED
                    ? "An approved request is revoked by the Admin or Director who approved it, not cancelled."
                    : "Only a pending request can be cancelled.");
        }
        request.cancel(clock.instant());
        requests.saveAndFlush(request);
        recorder.record(userId, AUDIT_TYPE, id, "status", Status.PENDING, Status.CANCELLED);
        events.publishEvent(new ManagerLeaveCancelled(id, who.managerId(), request.getFirstDate(), request.getLastDate()));
    }

    // ------------------------------------------------------------------ Admin and Director

    @Transactional(readOnly = true)
    public List<LeaveView> list(String status, UUID managerId) {
        List<ManagerLeaveRequest> found;
        if (status != null && !status.isBlank()) {
            Status wanted = parseStatus(status);
            found = managerId == null
                    ? requests.findByStatusOrderByRequestedAtAsc(wanted)
                    : requests.findByManagerIdAndStatusOrderByRequestedAtAsc(managerId, wanted);
        } else {
            found = managerId == null
                    ? requests.findAll(Sort.by("requestedAt").descending())
                    : requests.findByManagerIdOrderByRequestedAtDesc(managerId);
        }
        Map<UUID, String> names = managers.allManagers().stream()
                .collect(Collectors.toMap(ManagerRef::id, ManagerRef::displayName, (a, b) -> a));
        return found.stream().map(r -> view(r, names.get(r.getManagerId()))).toList();
    }

    @Transactional
    public LeaveView approve(UUID deciderUserId, UUID id) {
        ManagerLeaveRequest request = load(id);
        Who who = whoIsManager(request.getManagerId());
        requireStatusAndNotOwn(request, who, deciderUserId, Status.PENDING);
        LeaveType type = types.find(request.getLeaveTypeId())
                .orElseThrow(() -> new IllegalStateException("The leave type of the request no longer exists."));
        List<LocalDate> working =
                attendance.workingDays(who.managerId(), request.getFirstDate(), request.getLastDate());
        if (working.isEmpty()) {
            throw new ConflictException("The range no longer has a working day, so the request cannot be approved.");
        }
        boolean unpaid = isLossOfPay(type);
        List<LeaveDay> leaveDays = working.stream()
                .map(d -> new LeaveDay(
                        d,
                        (d.equals(request.getFirstDate()) && request.isFirstHalf())
                                || (d.equals(request.getLastDate()) && request.isLastHalf()),
                        unpaid))
                .toList();
        attendance.apply(id, deciderUserId, who.managerId(), leaveDays);
        request.decide(Status.APPROVED, deciderUserId, clock.instant(), null);
        requests.saveAndFlush(request);
        recorder.record(deciderUserId, AUDIT_TYPE, id, "status", Status.PENDING, Status.APPROVED);
        events.publishEvent(decided(request, Decision.APPROVED, null));
        return view(request, who.name());
    }

    @Transactional
    public LeaveView reject(UUID deciderUserId, UUID id, String reasonText) {
        String reason = text(reasonText, "A reason is required to reject a request.");
        ManagerLeaveRequest request = load(id);
        Who who = whoIsManager(request.getManagerId());
        requireStatusAndNotOwn(request, who, deciderUserId, Status.PENDING);
        request.decide(Status.REJECTED, deciderUserId, clock.instant(), reason);
        requests.saveAndFlush(request);
        recorder.record(deciderUserId, AUDIT_TYPE, id, "status", Status.PENDING, Status.REJECTED);
        recorder.record(deciderUserId, AUDIT_TYPE, id, "decisionReason", null, reason);
        events.publishEvent(decided(request, Decision.REJECTED, reason));
        return view(request, who.name());
    }

    @Transactional
    public LeaveView revoke(UUID deciderUserId, UUID id, String reasonText) {
        String reason = text(reasonText, "A reason is required to revoke an approved request.");
        ManagerLeaveRequest request = load(id);
        Who who = whoIsManager(request.getManagerId());
        requireStatusAndNotOwn(request, who, deciderUserId, Status.APPROVED);
        attendance.remove(id, deciderUserId);
        request.decide(Status.REVOKED, deciderUserId, clock.instant(), reason);
        requests.saveAndFlush(request);
        recorder.record(deciderUserId, AUDIT_TYPE, id, "status", Status.APPROVED, Status.REVOKED);
        recorder.record(deciderUserId, AUDIT_TYPE, id, "decisionReason", null, reason);
        events.publishEvent(decided(request, Decision.REVOKED, reason));
        return view(request, who.name());
    }

    // ------------------------------------------------------------------ helpers

    private ManagerLeaveRequest load(UUID id) {
        return requests.findById(id).orElseThrow(() -> new NotFoundException("That request was not found."));
    }

    private static void requireStatusAndNotOwn(ManagerLeaveRequest request, Who who, UUID deciderUserId, Status needed) {
        if (who.userId() != null && who.userId().equals(deciderUserId)) {
            throw new AccessDeniedException("You cannot decide your own request.");
        }
        if (request.getStatus() != needed) {
            throw new ConflictException("That request is " + request.getStatus().name().toLowerCase()
                    + ", so this cannot be done.");
        }
    }

    private static ManagerLeaveDecided decided(ManagerLeaveRequest r, Decision decision, String reason) {
        return new ManagerLeaveDecided(r.getId(), r.getManagerId(), decision, r.getFirstDate(), r.getLastDate(), reason);
    }

    private Who whoIsUser(UUID userId) {
        ManagerRef ref = managers.managerByUserId(userId)
                .orElseThrow(() -> new AccessDeniedException("The signed-in user is not linked to a Manager record."));
        return who(ref);
    }

    private Who whoIsManager(UUID managerId) {
        ManagerRef ref = managers.allManagers().stream()
                .filter(m -> m.id().equals(managerId))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Manager not found."));
        return who(ref);
    }

    private Who who(ManagerRef ref) {
        return new Who(ref.id(), ref.userId(), ref.displayName(), managers.employment(List.of(ref.id())).get(ref.id()));
    }

    private LeaveView view(ManagerLeaveRequest r, String managerName) {
        LeaveType type = types.find(r.getLeaveTypeId()).orElse(null);
        return new LeaveView(
                r.getId(),
                r.getManagerId(),
                managerName,
                r.getLeaveTypeId(),
                type == null ? null : type.getName(),
                type != null && isLossOfPay(type),
                r.getFirstDate(),
                r.getLastDate(),
                r.isFirstHalf(),
                r.isLastHalf(),
                r.getReason(),
                r.getStatus().name(),
                r.getRequestedAt(),
                r.getDecidedBy(),
                r.getDecidedAt(),
                r.getDecisionReason());
    }

    private static boolean isLossOfPay(LeaveType type) {
        return LeaveTypes.LOSS_OF_PAY_CODE.equals(type.getCode());
    }

    private static String text(String value, String missing) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty()) {
            throw new InvalidInputException(missing);
        }
        if (trimmed.length() > MAX_TEXT) {
            throw new InvalidInputException("The text can be at most " + MAX_TEXT + " characters.");
        }
        return trimmed;
    }

    private static UUID parseId(String value) {
        try {
            return UUID.fromString(value == null ? "" : value.trim());
        } catch (IllegalArgumentException e) {
            throw new InvalidInputException("Choose a leave type.");
        }
    }

    private static LocalDate parseDate(String value, String message) {
        try {
            return LocalDate.parse(value == null ? "" : value.trim());
        } catch (DateTimeParseException e) {
            throw new InvalidInputException(message);
        }
    }

    private static Status parseStatus(String value) {
        try {
            return Status.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new InvalidInputException("The status must be PENDING, APPROVED, REJECTED, CANCELLED or REVOKED.");
        }
    }
}
