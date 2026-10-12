package com.hls.attendance.manager.internal;

import com.hls.school.api.ChangeRecorder;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Publishes Manager attendance changes to the shared Change History (spec 032 data-model.md, "Audit entity types"). */
@Component
public class ManagerAttendanceAudit {

    public static final String DAY = "MANAGER_DAY";

    private final ChangeRecorder recorder;

    public ManagerAttendanceAudit(ChangeRecorder recorder) {
        this.recorder = recorder;
    }

    public void changed(UUID actor, String entityType, Object entityId, String field, Object before, Object after) {
        recorder.record(actor, entityType, entityId, field, before, after);
    }
}
