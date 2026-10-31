package com.hls.leave.internal;

import com.hls.attendance.api.LeaveAttendance;
import com.hls.identity.user.Role;
import com.hls.leave.internal.LeaveRequestService.Draft;
import com.hls.leave.internal.LeaveViewFactory.LeaveView;
import com.hls.leave.internal.LeaveViewFactory.Perspective;
import com.hls.school.api.InvalidInputException;
import com.hls.school.api.NotFoundException;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A Manager, Admin or Director records leave for a Teacher for today (spec 040 FR-001): it is Approved at once, the same
 * way as when the Teacher applies. Scope is the one leave management already uses; outside it the Teacher is not found.
 */
@Service
public class SameDayLeaveService {

    public record RecordRequest(
            UUID teacherId,
            UUID leaveTypeId,
            LocalDate firstDate,
            LocalDate lastDate,
            boolean halfDayStart,
            boolean halfDayEnd,
            String reason) {}

    private final LeaveRequestService requests;
    private final LeaveScope scope;
    private final LeaveAttendance attendance;

    public SameDayLeaveService(LeaveRequestService requests, LeaveScope scope, LeaveAttendance attendance) {
        this.requests = requests;
        this.scope = scope;
        this.attendance = attendance;
    }

    @Transactional(readOnly = true)
    public java.util.List<LeaveType> activeTypes() {
        return requests.activeTypes();
    }

    @Transactional
    public LeaveView record(UUID actor, Set<Role> roles, RecordRequest request) {
        if (request == null || request.teacherId() == null) {
            throw new InvalidInputException("Choose the Teacher.");
        }
        if (!scope.of(actor, roles).allows(request.teacherId())) {
            throw new NotFoundException("Teacher not found.");
        }
        if (request.firstDate() == null || !request.firstDate().equals(attendance.businessToday())) {
            throw new InvalidInputException("Same-day leave must start today.");
        }
        Draft draft = new Draft(
                request.leaveTypeId(),
                request.firstDate(),
                request.lastDate() == null ? request.firstDate() : request.lastDate(),
                request.halfDayStart(),
                request.halfDayEnd(),
                request.reason());
        return requests.submitFor(request.teacherId(), actor, draft, Perspective.SUPERVISOR);
    }
}
