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
            List<String> allowedActions) {}

    private final TeacherDirectory teachers;
    private final SchoolDirectory schools;
    private final LeaveTypeCatalog types;
    private final AppUserRepository users;
    private final LeaveAttendance attendance;

    public LeaveViewFactory(
            TeacherDirectory teachers,
            SchoolDirectory schools,
            LeaveTypeCatalog types,
            AppUserRepository users,
            LeaveAttendance attendance) {
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
                        .map(LeaveRequest::getDecidedByUserId)
                        .filter(java.util.Objects::nonNull)
                        .distinct()
                        .toList())
                .stream()
                .collect(Collectors.toMap(AppUser::getId, AppUser::getDisplayName, (a, b) -> a));
        LocalDate today = attendance.businessToday();
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
                        allowed(r, perspective, today)))
                .toList();
    }

    static List<String> allowed(LeaveRequest r, Perspective perspective, LocalDate today) {
        if (perspective == Perspective.OWN) {
            boolean cancellable = r.getStatus() == LeaveStatus.PENDING
                    || (r.getStatus() == LeaveStatus.APPROVED && r.getFirstDate().isAfter(today));
            return cancellable ? List.of("CANCEL") : List.of();
        }
        return switch (r.getStatus()) {
            case PENDING -> List.of("APPROVE", "REJECT");
            case APPROVED -> List.of("REVOKE");
            default -> List.of();
        };
    }

    /** Convenience for callers that need a map by id. */
    public Map<UUID, LeaveView> byId(Collection<LeaveRequest> requests, Perspective perspective) {
        return ofAll(requests, perspective).stream().collect(Collectors.toMap(LeaveView::id, Function.identity()));
    }
}
