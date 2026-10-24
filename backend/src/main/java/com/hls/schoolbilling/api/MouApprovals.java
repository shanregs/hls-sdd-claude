package com.hls.schoolbilling.api;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * Read-only access to the approval state of a School's MoU for specs 022 (billing), 030 (incentives) and 023 (marketing),
 * so they never read the approval tables. This only reports the state; whether a consumer waits for "approved" is that
 * consumer's rule. The MoU chosen is the one {@link SchoolContracts#contractOf} returns for the date.
 */
public interface MouApprovals {

    MouApprovalState stateOn(UUID schoolId, LocalDate date);

    /** {@link #stateOn} for many Schools at once, in a fixed number of queries; every given id has an entry. */
    Map<UUID, MouApprovalState> stateOfAll(Collection<UUID> schoolIds, LocalDate date);
}
