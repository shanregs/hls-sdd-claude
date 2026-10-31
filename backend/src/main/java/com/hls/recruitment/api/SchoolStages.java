package com.hls.recruitment.api;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * Where a School stands in the prospect-to-active journey, for the School report (spec 014b A5). It is the effective stage the
 * pipeline screen shows for the prospect linked to the School (Won, MoU or Active once the prospect is won). Read-only; callers
 * apply their own permission and scope.
 */
public interface SchoolStages {

    /** The stage name per School; a School with no prospect record is absent, and an empty set gives an empty map. */
    Map<UUID, String> stageOf(Collection<UUID> schoolIds);
}
