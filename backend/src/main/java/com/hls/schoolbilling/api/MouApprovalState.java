package com.hls.schoolbilling.api;

import java.time.Instant;
import java.util.UUID;

/**
 * The approval state of the MoU in effect for a School on a date (spec 031 contracts/module-interfaces.md).
 *
 * @param contractId null when {@code state} is NO_MOU
 * @param state NO_MOU, NOT_APPLICABLE, DRAFT, PENDING, REJECTED, APPROVED, APPROVED_WITHOUT_DOCUMENT or
 *     APPROVED_BEFORE_APPROVAL_STEP
 * @param approvedOn when the MoU was approved; null otherwise and for a MoU approved before the approval step
 * @param documentAttached a current, non-removed signed document exists
 * @param countsAsApproved true for APPROVED, APPROVED_WITHOUT_DOCUMENT and APPROVED_BEFORE_APPROVAL_STEP
 */
public record MouApprovalState(
        UUID schoolId,
        UUID contractId,
        String state,
        Instant approvedOn,
        boolean documentAttached,
        boolean countsAsApproved) {}
