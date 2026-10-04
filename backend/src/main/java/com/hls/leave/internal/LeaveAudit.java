package com.hls.leave.internal;

import com.hls.school.api.ChangeRecorder;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Publishes leave request changes to the shared Change History (spec 009 FR-011). The entity type is
 * mapped to the VIEW grant that may read it in {@code AuditVisibility}.
 */
@Component
public class LeaveAudit {

    public static final String REQUEST = "LEAVE_REQUEST";

    private final ChangeRecorder recorder;

    public LeaveAudit(ChangeRecorder recorder) {
        this.recorder = recorder;
    }

    public void created(UUID actor, LeaveRequest request) {
        recorder.recordLifecycle(
                actor,
                REQUEST,
                request.getId(),
                "created",
                "teacher " + request.getTeacherId() + " " + request.getFirstDate() + ".." + request.getLastDate()
                        + " (" + request.getWorkingDays().toPlainString() + " working days)");
    }

    public void status(UUID actor, LeaveRequest request, LeaveStatus before, String detail) {
        recorder.record(
                actor,
                REQUEST,
                request.getId(),
                "status",
                before,
                request.getStatus() + (detail == null || detail.isBlank() ? "" : " - " + detail)
                        + " (teacher " + request.getTeacherId() + ")");
    }
}
