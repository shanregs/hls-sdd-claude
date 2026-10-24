package com.hls.recruitment.internal;

import com.hls.recruitment.api.ActivityEntry;
import com.hls.recruitment.api.DriveActivities;
import com.hls.recruitment.api.PlannedActivity;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class DriveActivitiesImpl implements DriveActivities {

    private final DriveService drives;
    private final CampusDriveRepository driveRows;
    private final DriveDateChangeRepository dateChanges;

    DriveActivitiesImpl(DriveService drives, CampusDriveRepository driveRows, DriveDateChangeRepository dateChanges) {
        this.drives = drives;
        this.driveRows = driveRows;
        this.dateChanges = dateChanges;
    }

    @Override
    @Transactional(readOnly = true)
    public List<PlannedActivity> plannedBetween(LocalDate from, LocalDate to, UUID ownerUserId) {
        List<PlannedActivity> out = new ArrayList<>();
        for (DriveService.DriveDto d : drives.list(ownerUserId, from, to, null, ownerUserId != null)) {
            String place = d.college().name() + ", " + d.college().city();
            for (LocalDate date : d.dates()) {
                if ((from == null || !date.isBefore(from)) && (to == null || !date.isAfter(to))) {
                    out.add(new PlannedActivity("CAMPUS_DRIVE", d.id(), d.scheduledBy(), date, place, d.status()));
                }
            }
        }
        out.sort(Comparator.comparing(PlannedActivity::date));
        return out;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ActivityEntry> activitiesBetween(LocalDate from, LocalDate to, UUID userId) {
        List<DriveService.DriveDto> found = drives.list(userId, from, to, null, userId != null);
        if (found.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = found.stream().map(DriveService.DriveDto::id).toList();
        Map<UUID, LocalDate> heldOn = new HashMap<>();
        for (CampusDrive row : driveRows.findAllById(ids)) {
            heldOn.put(row.getId(), row.getHeldOn());
        }
        // new date -> old date, per drive, oldest change first; followed back to the first old date
        Map<UUID, Map<LocalDate, LocalDate>> oldOf = new HashMap<>();
        for (DriveDateChange c : dateChanges.findByDriveIdInOrderByChangedAtAsc(ids)) {
            oldOf.computeIfAbsent(c.getDriveId(), k -> new HashMap<>()).put(c.getNewDate(), c.getOldDate());
        }
        List<ActivityEntry> out = new ArrayList<>();
        for (DriveService.DriveDto d : found) {
            String place = d.college().name() + ", " + d.college().city();
            Set<UUID> attendees = d.interviewers().stream()
                    .map(DriveService.PersonRef::userId)
                    .collect(Collectors.toCollection(HashSet::new));
            LocalDate last = d.dates().stream().max(Comparator.naturalOrder()).orElse(null);
            LocalDate held = heldOn.get(d.id());
            for (LocalDate date : d.dates()) {
                if ((from != null && date.isBefore(from)) || (to != null && date.isAfter(to))) {
                    continue;
                }
                LocalDate completedOn = null;
                if ("HELD".equals(d.status()) && held != null) {
                    completedOn = date.equals(last) ? held : (held.isBefore(date) ? held : date);
                }
                out.add(new ActivityEntry(
                        "CAMPUS_DRIVE",
                        d.id(),
                        d.scheduledBy(),
                        attendees,
                        date,
                        firstDate(oldOf.get(d.id()), date),
                        place,
                        null,
                        null,
                        null,
                        null,
                        d.status(),
                        completedOn));
            }
        }
        out.sort(Comparator.comparing(ActivityEntry::date));
        return out;
    }

    /** Follows the moves back from {@code date} to the first date it had; null when it never moved. */
    private static LocalDate firstDate(Map<LocalDate, LocalDate> oldOfNew, LocalDate date) {
        if (oldOfNew == null) {
            return null;
        }
        LocalDate current = date;
        Set<LocalDate> seen = new HashSet<>();
        while (oldOfNew.containsKey(current) && seen.add(current)) {
            current = oldOfNew.get(current);
        }
        return current.equals(date) ? null : current;
    }
}
