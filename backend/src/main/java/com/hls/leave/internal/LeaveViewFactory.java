package com.hls.leave.internal;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.hls.attendance.api.LeaveAttendance;
import com.hls.identity.user.AppUser;
import com.hls.identity.user.AppUserRepository;
import com.hls.school.api.SchoolDirectory;
import com.hls.teacher.api.TeacherDirectory;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Builds {@link LeaveView}s, resolving Teacher, School, type and decider names in bulk. */
@Component
public class LeaveViewFactory {

    /** Whose point of view the actions are computed for. */
    public enum Perspective {
        OWN,
        SUPERVISOR
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record LeaveView(
            UUID id,
            UUID teacherId,
            String teacherName,
            UUID schoolId,
            String schoolName,
            String leaveType,
            LocalDate firstDate,
            LocalDate lastDate,
            boolean halfDayStart,
            boolean halfDayEnd,
            BigDecimal workingDays,
            String reason,
            String status,
            String decidedByName,
            Instant decidedAt,
            String decisionNote,
            String cancelledBy,
            Instant createdAt,
            Long version,
            boolean sameDay,
            String recordedByName,
            OtherPart otherPart,
            List<String> allowedActions) {}

    /** The other half of a request split at today (spec 040): the same-day part and the later part point at each other. */
    public record OtherPart(UUID id, LocalDate firstDate, LocalDate lastDate, String status) {}

    private final LeaveRequestRepository requests;
    private final TeacherDirectory teachers;
    private final SchoolDirectory schools;
    private final LeaveTypeCatalog types;
    private final AppUserRepository users;
    private final LeaveAttendance attendance;

    public LeaveViewFactory(
            LeaveRequestRepository requests,
            TeacherDirectory teachers,
            SchoolDirectory schools,
            LeaveTypeCatalog types,
            AppUserRepository users,
            LeaveAttendance attendance) {
        this.requests = requests;
        this.teachers = teachers;
        this.schools = schools;
        this.types = types;
        this.users = users;
        this.attendance = attendance;
    }

    @Transactional(readOnly = true)
    public LeaveView of(LeaveRequest request, Perspective perspective) {
        return ofAll(List.of(request), perspective).get(0);
    }

    @Transactional(readOnly = true)
    public List<LeaveView> ofAll(Collection<LeaveRequest> requests, Perspective perspective) {
        if (requests.isEmpty()) {
            return List.of();
        }
        Map<UUID, String> teacherNames = teachers
                .teacherInfo(requests.stream().map(LeaveRequest::getTeacherId).distinct().toList())
                .values()
                .stream()
                .collect(Collectors.toMap(TeacherDirectory.TeacherInfo::id, TeacherDirectory.TeacherInfo::name));
        Map<UUID, String> schoolNames = schools
                .schools(requests.stream().map(LeaveRequest::getSchoolId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(SchoolDirectory.SchoolInfo::id, SchoolDirectory.SchoolInfo::name));
        Map<UUID, String> typeNames = types.namesById();
        Map<UUID, String> userNames = users
                .findAllById(requests.stream()
                        .flatMap(r -> java.util.stream.Stream.of(r.getDecidedByUserId(), r.getCreatedByUserId()))
                        .filter(java.util.Objects::nonNull)
                        .distinct()
                        .toList())
                .stream()
                .collect(Collectors.toMap(AppUser::getId, AppUser::getDisplayName, (a, b) -> a));
        LocalDate today = attendance.businessToday();
        Map<UUID, LeaveRequest> parts = new java.util.HashMap<>();
        var linkedIds = requests.stream()
                .filter(r -> r.isSameDay() || r.getLinkedRequestId() != null)
                .flatMap(r -> java.util.stream.Stream.of(r.getId(), r.getLinkedRequestId()))
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        if (!linkedIds.isEmpty()) {
            this.requests.findPartsOf(linkedIds).forEach(p -> parts.put(p.getId(), p));
        }
        return requests.stream()
                .map(r -> new LeaveView(
                        r.getId(),
                        r.getTeacherId(),
                        teacherNames.getOrDefault(r.getTeacherId(), "-"),
                        r.getSchoolId(),
                        schoolNames.getOrDefault(r.getSchoolId(), "-"),
                        typeNames.getOrDefault(r.getLeaveTypeId(), "-"),
                        r.getFirstDate(),
                        r.getLastDate(),
                        r.isHalfDayStart(),
                        r.isHalfDayEnd(),
                        r.getWorkingDays(),
                        r.getReason(),
                        r.getStatus().name(),
                        r.getDecidedByUserId() == null ? null : userNames.getOrDefault(r.getDecidedByUserId(), "Unknown user"),
                        r.getDecidedAt(),
                        r.getDecisionNote(),
                        r.getCancelledByKind(),
                        r.getCreatedAt(),
                        r.getVersion(),
                        r.isSameDay(),
                        r.isSameDay() ? userNames.getOrDefault(r.getCreatedByUserId(), "Unknown user") : null,
                        otherPart(r, parts),
                        allowed(r, perspective, today)))
                .toList();
    }

    static List<String> allowed(LeaveRequest r, Perspective perspective, LocalDate today) {
        if (perspective == Perspective.OWN) {
            boolean cancellable = r.getStatus() == LeaveStatus.PENDING
                    || (r.getStatus() == LeaveStatus.APPROVED
                            && (r.getFirstDate().isAfter(today) || (r.isSameDay() && r.getFirstDate().equals(today))));
            return cancellable ? List.of("CANCEL") : List.of();
        }
        return switch (r.getStatus()) {
            case PENDING -> List.of("APPROVE", "REJECT");
            case APPROVED -> List.of("REVOKE");
            default -> List.of();
        };
    }

    private static OtherPart otherPart(LeaveRequest r, Map<UUID, LeaveRequest> parts) {
        LeaveRequest other = null;
        if (r.getLinkedRequestId() != null) {
            other = parts.get(r.getLinkedRequestId());
        } else if (r.isSameDay()) {
            other = parts.values().stream()
                    .filter(p -> r.getId().equals(p.getLinkedRequestId()))
                    .findFirst()
                    .orElse(null);
        }
        return other == null
                ? null
                : new OtherPart(other.getId(), other.getFirstDate(), other.getLastDate(), other.getStatus().name());
    }

    /** Convenience for callers that need a map by id. */
    public Map<UUID, LeaveView> byId(Collection<LeaveRequest> requests, Perspective perspective) {
        return ofAll(requests, perspective).stream().collect(Collectors.toMap(LeaveView::id, Function.identity()));
    }
}
