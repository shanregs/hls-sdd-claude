package com.hls.recruitment.api;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/** Read-only, bulk lookup of the Zone of marketing prospects (spec 035). No scope is applied; the caller does that. */
public interface ProspectZones {

    /** Prospect id to Zone id, for the ids that exist. */
    Map<UUID, UUID> zonesOf(Collection<UUID> prospectIds);
}
