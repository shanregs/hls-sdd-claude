package com.hls.attendance.internal;

import com.hls.school.api.ChangeRecorder;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Publishes attendance changes to the shared Change History (spec 008 FR-021). The entity types are
 * mapped to the VIEW grant that may read them in {@code AuditVisibility}.
 */
@Component
public class AttendanceAudit {

    public static final String MARK = "ATTENDANCE_MARK";
    public static final String MONTH = "ATTENDANCE_MONTH";
    public static final String CODE = "ATTENDANCE_CODE";
    public static final String CALENDAR = "ATTENDANCE_CALENDAR";
    public static final String EXPORT = "ATTENDANCE_EXPORT";

    private final ChangeRecorder recorder;

    public AttendanceAudit(ChangeRecorder recorder) {
        this.recorder = recorder;
    }

    public void changed(UUID actor, String entityType, Object entityId, String field, Object before, Object after) {
        recorder.record(actor, entityType, entityId, field, before, after);
    }

    public void lifecycle(UUID actor, String entityType, Object entityId, String field, Object detail) {
        recorder.recordLifecycle(actor, entityType, entityId, field, detail);
    }
}
