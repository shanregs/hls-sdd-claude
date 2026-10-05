package com.hls.recruitment.internal;

import com.hls.recruitment.api.DriveActivities;
import com.hls.recruitment.api.PlannedActivity;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class DriveActivitiesImpl implements DriveActivities {

    private final DriveService drives;

    DriveActivitiesImpl(DriveService drives) {
        this.drives = drives;
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
}
