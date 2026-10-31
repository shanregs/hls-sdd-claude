package com.hls.recruitment.marketing.internal;

import com.hls.recruitment.api.ActivityEntry;
import com.hls.recruitment.api.MarketingActivities;
import com.hls.recruitment.api.PlannedActivity;
import com.hls.school.api.SchoolDirectory;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Marketing activities as planned activities (contract C1): planned ones only, in the date range. */
@Service
class MarketingActivitiesImpl implements MarketingActivities {

    private final MarketingActivityRepository activities;
    private final ProspectRepository prospects;
    private final ActivityDateHistoryRepository dateHistory;
    private final SchoolDirectory schools;

    MarketingActivitiesImpl(
            MarketingActivityRepository activities,
            ProspectRepository prospects,
            ActivityDateHistoryRepository dateHistory,
            SchoolDirectory schools) {
        this.activities = activities;
        this.prospects = prospects;
        this.dateHistory = dateHistory;
        this.schools = schools;
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

    /** Spec 035: every visit, call and meeting in the range in any status; the history is read in one bulk query. */
    @Override
    @Transactional(readOnly = true)
    public List<ActivityEntry> activitiesBetween(LocalDate from, LocalDate to, UUID userId) {
        List<MarketingActivity> found = activities.findByActivityDateBetweenOrderByActivityDate(
                from == null ? LocalDate.of(2000, 1, 1) : from, to == null ? LocalDate.of(2100, 1, 1) : to);
        if (userId != null) {
            found = found.stream()
                    .filter(a -> a.getCreatedBy().equals(userId) || a.getAttendees().contains(userId))
                    .toList();
        }
        if (found.isEmpty()) {
            return List.of();
        }
        Map<UUID, Prospect> prospectById = prospects
                .findAllById(found.stream().map(MarketingActivity::getProspectId).filter(java.util.Objects::nonNull).distinct().toList())
                .stream()
                .collect(Collectors.toMap(Prospect::getId, p -> p));
        Map<UUID, SchoolDirectory.SchoolInfo> schoolById = schools
                .schools(found.stream().map(MarketingActivity::getSchoolId).filter(java.util.Objects::nonNull).distinct().toList())
                .stream()
                .collect(Collectors.toMap(SchoolDirectory.SchoolInfo::id, s -> s));
        // the first planned date of a moved activity: the old date of its earliest change
        Map<UUID, ActivityDateHistory> earliest = new HashMap<>();
        for (ActivityDateHistory h : dateHistory.findByActivityIdIn(found.stream().map(MarketingActivity::getId).toList())) {
            earliest.merge(h.getActivityId(), h, (a, b) -> b.getChangedAt().isBefore(a.getChangedAt()) ? b : a);
        }
        List<ActivityEntry> out = new ArrayList<>();
        for (MarketingActivity a : found) {
            Prospect p = a.getProspectId() == null ? null : prospectById.get(a.getProspectId());
            SchoolDirectory.SchoolInfo school = a.getSchoolId() == null ? null : schoolById.get(a.getSchoolId());
            String place = p != null ? p.getName() : (school != null ? school.name() : null);
            java.util.UUID zone = p != null ? p.getZoneId() : (school != null ? school.zoneId() : null);
            ActivityDateHistory first = earliest.get(a.getId());
            out.add(new ActivityEntry(
                    "SCHOOL_" + a.getType().name(),
                    a.getId(),
                    a.getCreatedBy(),
                    Set.copyOf(a.getAttendees()),
                    a.getActivityDate(),
                    first == null ? null : first.getOldDate(),
                    place,
                    a.getSchoolId(),
                    zone,
                    null,
                    null,
                    a.getStatus().name(),
                    a.getCompletedOn(),
                    a.isSameDay()));
        }
        out.sort(Comparator.comparing(ActivityEntry::date));
        return out;
    }
}
