package com.hls.audit.api;

import com.hls.identity.user.Role;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * A read-only summary of the newest audit entries, for dashboards (spec 011). It never returns an entry the caller's
 * roles may not see in Change History.
 */
public interface RecentAudit {

    /** @param kind {@code CHANGE} or {@code LOGIN}; {@code actorUserId} may be null (an unknown sign-in). */
    record Event(String kind, Instant at, UUID actorUserId, String summary) {}

    /** The newest {@code limit} change and sign-in entries, newest first, that the caller's roles may see. */
    List<Event> recent(Set<Role> callerRoles, int limit);

    /** How many failed sign-ins were recorded since {@code since}. */
    long failedSignInsSince(Instant since);
}
