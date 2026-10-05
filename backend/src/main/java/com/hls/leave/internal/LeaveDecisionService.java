package com.hls.leave.internal;

import com.hls.attendance.api.LeaveAttendance;
import com.hls.identity.user.Role;
import com.hls.leave.api.LeaveDecided;
import com.hls.leave.api.LeaveDecided.Decision;
import com.hls.leave.internal.LeaveRequestService.PreviewDay;
import com.hls.leave.internal.LeaveScope.Scope;
import com.hls.leave.internal.LeaveViewFactory.LeaveView;
import com.hls.leave.internal.LeaveViewFactory.Perspective;
import com.hls.school.api.ConflictException;
import com.hls.school.api.InvalidInputException;
import com.hls.school.api.NotFoundException;
import com.hls.school.api.StaleVersion;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A supervisor's side of leave (spec 009 US3, US4): the scoped list, and approve, reject and revoke.
 * Authorization is decided by the caller; scope is enforced here through {@link LeaveScope}.
 */
@Service
public class LeaveDecisionService {

    public static final int MAX_NOTE = 500;

    /** A page of requests plus how many Pending ones the caller may decide. */
    public record ListResponse(List<LeaveView> content, int page, int size, long totalElements, long pendingCount) {}

    /** One request with the days it would mark (or has marked) and what would stop an approval. */
    public record Detail(LeaveView request, List<PreviewDay> days, List<String> problems) {}

    private final LeaveRequestRepository requests;
    private final LeaveScope scope;
    private final LeaveAttendance attendance;
    private final LeaveViewFactory views;
    private final LeaveAudit audit;
    private final Clock clock;
    private final ApplicationEventPublisher events;

    public LeaveDecisionService(
            LeaveRequestRepository requests,
            LeaveScope scope,
            LeaveAttendance attendance,
            LeaveViewFactory views,
            LeaveAudit audit,
            Clock clock,
            ApplicationEventPublisher events) {
        this.requests = requests;
        this.scope = scope;
        this.attendance = attendance;
        this.views = views;
        this.audit = audit;
        this.clock = clock;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public ListResponse list(
            UUID userId,
            Set<Role> roles,
            LeaveStatus status,
            UUID teacherId,
            UUID schoolId,
            YearMonth month,
            int page,
            int size) {
        Scope s = scope.of(userId, roles);
        var statuses = status == null ? EnumSet.of(LeaveStatus.PENDING) : EnumSet.of(status);
        LocalDate from = month == null ? LocalDate.of(1900, 1, 1) : month.atDay(1);
        LocalDate to = month == null ? LocalDate.of(2999, 12, 31) : month.atEndOfMonth();
        var result = requests.findInScope(
                statuses,
                s.orgWide(),
                s.teacherIds(),
                teacherId,
                schoolId,
                from,
                to,
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100)));
        return new ListResponse(
                views.ofAll(result.getContent(), Perspective.SUPERVISOR),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                requests.countPendingInScope(s.orgWide(), s.teacherIds()));
    }

    @Transactional(readOnly = true)
    public Detail detail(UUID userId, Set<Role> roles, UUID id) {
        LeaveRequest request = inScope(userId, roles, requests.findById(id));
        var counted = LeaveCounter.count(
                attendance.workingDays(request.getTeacherId(), request.getFirstDate(), request.getLastDate()),
                request.isHalfDayStart(),
                request.isHalfDayEnd());
        List<PreviewDay> days = counted.ok()
                ? counted.days().stream().map(d -> new PreviewDay(d.date(), d.value())).toList()
                : List.of();
        List<String> problems = request.getStatus() == LeaveStatus.PENDING && counted.ok()
                ? attendance.problems(request.getTeacherId(), counted.days())
                : List.of();
        return new Detail(views.of(request, Perspective.SUPERVISOR), days, problems);
    }

    @Transactional
    public LeaveView approve(UUID actor, Set<Role> roles, UUID id, String note, Long version) {
        LeaveRequest request = lockedInScope(actor, roles, id);
        StaleVersion.check(LeaveRequest.class, id, request.getVersion(), version);
        requirePending(request);
        String cleanNote = cleanNote(note, false);

        var counted = LeaveCounter.count(
                attendance.workingDays(request.getTeacherId(), request.getFirstDate(), request.getLastDate()),
                request.isHalfDayStart(),
                request.isHalfDayEnd());
        if (!counted.ok()) {
            throw new ConflictException(counted.problem());
        }
        boolean overlaps = requests
                .findLiveOverlapping(request.getTeacherId(), request.getFirstDate(), request.getLastDate())
                .stream()
                .anyMatch(other -> !other.getId().equals(request.getId()));
        if (overlaps) {
            throw new ConflictException("This request now overlaps another live leave request of the Teacher.");
        }
        attendance.apply(request.getId(), actor, request.getTeacherId(), counted.days());

        LeaveStatus before = request.getStatus();
        request.approve(actor, cleanNote, counted.total(), clock.instant());
        LeaveRequest saved = requests.saveAndFlush(request);
        audit.status(actor, saved, before, cleanNote);
        publish(saved, Decision.APPROVED, null);
        return views.of(saved, Perspective.SUPERVISOR);
    }

    @Transactional
    public LeaveView reject(UUID actor, Set<Role> roles, UUID id, String reason, Long version) {
        LeaveRequest request = lockedInScope(actor, roles, id);
        StaleVersion.check(LeaveRequest.class, id, request.getVersion(), version);
        requirePending(request);
        String cleanReason = cleanNote(reason, true);

        LeaveStatus before = request.getStatus();
        request.reject(actor, cleanReason, clock.instant());
        LeaveRequest saved = requests.saveAndFlush(request);
        audit.status(actor, saved, before, cleanReason);
        publish(saved, Decision.REJECTED, cleanReason);
        return views.of(saved, Perspective.SUPERVISOR);
    }

    @Transactional
    public LeaveView revoke(UUID actor, Set<Role> roles, UUID id, String reason, Long version) {
        LeaveRequest request = lockedInScope(actor, roles, id);
        StaleVersion.check(LeaveRequest.class, id, request.getVersion(), version);
        if (request.getStatus() != LeaveStatus.APPROVED) {
            throw new ConflictException(statusMessage(request, "Only an approved request can be revoked."));
        }
        String cleanReason = cleanNote(reason, true);

        attendance.remove(request.getId(), actor);
        LeaveStatus before = request.getStatus();
        request.cancel("SUPERVISOR", actor, cleanReason, clock.instant());
        LeaveRequest saved = requests.saveAndFlush(request);
        audit.status(actor, saved, before, "revoked: " + cleanReason);
        publish(saved, Decision.REVOKED, cleanReason);
        return views.of(saved, Perspective.SUPERVISOR);
    }

    /** Tells the notification module inside this transaction, so a failed decision leaves no notification. */
    private void publish(LeaveRequest request, Decision decision, String reason) {
        events.publishEvent(new LeaveDecided(
                request.getId(), request.getTeacherId(), decision, request.getFirstDate(), request.getLastDate(), reason));
    }

    private LeaveRequest lockedInScope(UUID userId, Set<Role> roles, UUID id) {
        return inScope(userId, roles, requests.findByIdForUpdate(id));
    }

    private LeaveRequest inScope(UUID userId, Set<Role> roles, java.util.Optional<LeaveRequest> found) {
        Scope s = scope.of(userId, roles);
        return found.filter(r -> s.allows(r.getTeacherId()))
                .orElseThrow(() -> new NotFoundException("Leave request not found."));
    }

    private static void requirePending(LeaveRequest request) {
        if (request.getStatus() != LeaveStatus.PENDING) {
            throw new ConflictException(statusMessage(request, "Only a pending request can be decided."));
        }
    }

    private static String statusMessage(LeaveRequest request, String fallback) {
        return switch (request.getStatus()) {
            case CANCELLED -> "This request was cancelled"
                    + ("TEACHER".equals(request.getCancelledByKind()) ? " by the Teacher." : ".");
            case APPROVED -> "This request was already approved.";
            case REJECTED -> "This request was already rejected.";
            default -> fallback;
        };
    }

    private static String cleanNote(String text, boolean required) {
        if (text == null || text.isBlank()) {
            if (required) {
                throw new InvalidInputException("A reason is required.");
            }
            return null;
        }
        String trimmed = text.trim();
        if (trimmed.length() > MAX_NOTE) {
            throw new InvalidInputException("The note can be at most " + MAX_NOTE + " characters.");
        }
        return trimmed;
    }
}
