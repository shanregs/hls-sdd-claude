package com.hls.performance.internal;

import com.hls.performance.api.PerformanceReferrals;
import com.hls.performance.internal.PerformanceTypes.IssueStatus;
import com.hls.performance.internal.PerformanceTypes.Outcome;
import com.hls.school.api.InvalidInputException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The referrals read by spec 027, with their supporting observations and feedback (spec 026 FR-023). */
@Service
class PerformanceReferralsImpl implements PerformanceReferrals {

    private final IssueRepository issues;
    private final IssueLinkRepository links;
    private final ReferralRepository referrals;
    private final ObservationRepository observations;
    private final FeedbackRepository feedback;
    private final IssueService issueService;

    PerformanceReferralsImpl(
            IssueRepository issues,
            IssueLinkRepository links,
            ReferralRepository referrals,
            ObservationRepository observations,
            FeedbackRepository feedback,
            IssueService issueService) {
        this.issues = issues;
        this.links = links;
        this.referrals = referrals;
        this.observations = observations;
        this.feedback = feedback;
        this.issueService = issueService;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReferralInfo> referredFor(UUID teacherId) {
        List<ReferralInfo> result = new ArrayList<>();
        for (PerformanceIssue issue : issues.findByTeacherIdOrderByOpenedOnDescCreatedAtDesc(teacherId)) {
            if (issue.status == IssueStatus.REFERRED) {
                info(issue).ifPresent(result::add);
            }
        }
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ReferralInfo> referral(UUID issueId) {
        return issues.findById(issueId).flatMap(this::info);
    }

    @Override
    @Transactional
    public void reportDecision(UUID issueId, String outcome, UUID actorUserId) {
        Outcome parsed;
        try {
            parsed = Outcome.valueOf(outcome == null ? "" : outcome.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new InvalidInputException("The outcome must be TRANSFERRED or REPLACED.");
        }
        issueService.reportDecision(issueId, parsed, actorUserId);
    }

    private Optional<ReferralInfo> info(PerformanceIssue issue) {
        return referrals.findByIssueId(issue.id).map(r -> {
            List<LinkedRecord> supporting = new ArrayList<>();
            for (IssueLink l : links.findByIssueIdOrderByLinkedOn(issue.id)) {
                if (l.recordType == PerformanceTypes.SourceType.OBSERVATION) {
                    observations.findByRootIdAndCurrentTrue(l.recordRootId).ifPresent(o -> supporting.add(new LinkedRecord("OBSERVATION", o.id,
                            o.observedOn, o.overallResult == null ? null : o.overallResult.name(), o.improvements != null ? o.improvements : o.strengths)));
                } else {
                    feedback.findByRootIdAndCurrentTrue(l.recordRootId).ifPresent(f -> supporting.add(new LinkedRecord("FEEDBACK", f.id, f.givenOn,
                            null, f.comments)));
                }
            }
            return new ReferralInfo(issue.id, issue.teacherId, issue.schoolId, r.recommendation.name(), r.reason, r.referredBy, r.referredOn, supporting);
        });
    }
}
