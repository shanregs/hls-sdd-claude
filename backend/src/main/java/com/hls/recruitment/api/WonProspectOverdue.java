package com.hls.recruitment.api;

import java.util.UUID;

/**
 * Published once when a won prospect has no MoU recorded after the allowed number of days (spec 023). The
 * notification module tells the owner, the Zone Manager(s) of the Zone, and every active Admin and Director.
 */
public record WonProspectOverdue(UUID prospectId, String name, UUID ownerUserId, UUID zoneId, int days) {}
