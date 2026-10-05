package com.hls.recruitment.marketing.internal;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * "Follow-up overdue" is derived, never stored: a completed activity whose follow-up date has passed, with no later
 * activity (planned or done) on the prospect. It clears itself as soon as a later activity exists.
 */
@Component
class FollowUps {

    private final MarketingActivityRepository activities;
    private final Clock clock;

    FollowUps(MarketingActivityRepository activities, Clock clock) {
        this.activities = activities;
        this.clock = clock;
    }

    Set<UUID> overdueProspects(Collection<UUID> prospectIds) {
        if (prospectIds.isEmpty()) {
            return Set.of();
        }
        LocalDate today = LocalDate.now(clock);
        Map<UUID, List<MarketingActivity>> byProspect =
                activities.findByProspectIdIn(prospectIds).stream().collect(Collectors.groupingBy(MarketingActivity::getProspectId));
        Set<UUID> overdue = new HashSet<>();
        byProspect.forEach((prospect, all) -> {
            boolean flagged = all.stream()
                    .filter(a -> a.getStatus() == ActivityStatus.COMPLETED
                            && a.getFollowUpOn() != null
                            && a.getFollowUpOn().isBefore(today))
                    .anyMatch(a -> all.stream()
                            .noneMatch(o -> !o.getId().equals(a.getId())
                                    && o.getStatus() != ActivityStatus.CANCELLED
                                    && o.getActivityDate().isAfter(a.getActivityDate())));
            if (flagged) {
                overdue.add(prospect);
            }
        });
        return overdue;
    }
}
