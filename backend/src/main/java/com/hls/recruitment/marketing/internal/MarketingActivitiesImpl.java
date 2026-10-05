package com.hls.recruitment.marketing.internal;

import com.hls.recruitment.api.MarketingActivities;
import com.hls.recruitment.api.PlannedActivity;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Marketing activities as planned activities (contract C1): planned ones only, in the date range. */
@Service
class MarketingActivitiesImpl implements MarketingActivities {

    private final MarketingActivityRepository activities;
    private final ProspectRepository prospects;

    MarketingActivitiesImpl(MarketingActivityRepository activities, ProspectRepository prospects) {
        this.activities = activities;
        this.prospects = prospects;
    }

    @Override
    @Transactional(readOnly = true)
    public List<PlannedActivity> plannedBetween(LocalDate from, LocalDate to, UUID ownerUserId) {
        List<MarketingActivity> found = activities.findByActivityDateBetweenOrderByActivityDate(
                from == null ? LocalDate.of(2000, 1, 1) : from, to == null ? LocalDate.of(2100, 1, 1) : to);
        Map<UUID, Prospect> byId = prospects
                .findAllById(found.stream().map(MarketingActivity::getProspectId).filter(java.util.Objects::nonNull).toList())
                .stream()
                .collect(Collectors.toMap(Prospect::getId, p -> p));
        List<PlannedActivity> out = new ArrayList<>();
        for (MarketingActivity a : found) {
            if (a.getStatus() != ActivityStatus.PLANNED) {
                continue;
            }
            if (ownerUserId != null && !a.getAttendees().contains(ownerUserId) && !a.getCreatedBy().equals(ownerUserId)) {
                continue;
            }
            Prospect p = a.getProspectId() == null ? null : byId.get(a.getProspectId());
            out.add(new PlannedActivity(
                    "SCHOOL_" + a.getType().name(),
                    a.getId(),
                    a.getCreatedBy(),
                    a.getActivityDate(),
                    p == null ? null : p.getName(),
                    a.getStatus().name()));
        }
        out.sort(Comparator.comparing(PlannedActivity::date));
        return out;
    }
}
