package com.hls.attendance.api;

import java.util.Map;
import java.util.UUID;

/** How many forgotten check-in requests wait for a decision, per Manager id (spec 028 reads it for the Dashboard). */
public interface PendingForgottenCheckIns {

    /** Pending requests keyed by Manager id; a Manager with none is absent. One query. */
    Map<UUID, Long> pendingByManager();
}
