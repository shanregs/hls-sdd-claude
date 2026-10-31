package com.hls.substitution.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * How the classes left uncovered at a School were covered, for the School report (spec 014b A3). Read-only and bulk: a fixed
 * number of queries whatever the number of Schools. It checks no permission; callers apply their own scope.
 */
public interface SubstitutionCover {

    /**
     * Day values of the requirements raised for the School in the range: covered by an assigned substitute, covered by a
     * Manager, and still open (a half day counts 0.5; cancelled requirements are left out).
     */
    record SchoolCover(UUID schoolId, BigDecimal bySubstitute, BigDecimal byManager, BigDecimal open) {}

    /** Only Schools with at least one live requirement in {@code from..to} have an entry; an empty set gives an empty map. */
    Map<UUID, SchoolCover> coverBySchool(Collection<UUID> schoolIds, LocalDate from, LocalDate to);
}
