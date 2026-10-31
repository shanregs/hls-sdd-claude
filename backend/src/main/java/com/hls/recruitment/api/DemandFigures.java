package com.hls.recruitment.api;

import java.util.Collection;
import java.util.UUID;

/**
 * Demand for Teachers, for reports (spec 014c A2): the vacant positions of won Schools with a live contract, by the same
 * rule as the marketing dashboard (spec 023). Read-only; callers apply their own permission.
 */
public interface DemandFigures {

    /** Every Zone. */
    long vacantPositionsOfWonSchools();

    /** Only won Schools of the given Zones (a Zone Manager's scope); an empty collection gives 0. */
    long vacantPositionsOfWonSchools(Collection<UUID> zoneIds);
}
