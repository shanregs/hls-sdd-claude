package com.hls.performance.api;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Referrals for spec 027 (transfer and replacement). Read-only apart from {@link #reportDecision}, which only closes a
 * Referred issue. No caller scope is applied: the calling module applies its own. A referral never changes a Teacher's
 * placement, status or pay (spec 026 FR-033).
 */
public interface PerformanceReferrals {

    /** One observation or Principal feedback that supports a referral. */
    record LinkedRecord(String type, UUID id, LocalDate date, String result, String summary) {}

    /** {@code recommendation} is TRANSFER or REPLACE. */
    record ReferralInfo(
            UUID issueId,
            UUID teacherId,
            UUID schoolId,
            String recommendation,
            String reason,
            UUID referredBy,
            LocalDate referredOn,
            List<LinkedRecord> supporting) {}

    /** The Teacher's issues that are Referred and not yet decided. */
    List<ReferralInfo> referredFor(UUID teacherId);

    Optional<ReferralInfo> referral(UUID issueId);

    /** 027 reports the decision: {@code outcome} is TRANSFERRED or REPLACED. Refuses an issue that is not Referred. */
    void reportDecision(UUID issueId, String outcome, UUID actorUserId);
}
