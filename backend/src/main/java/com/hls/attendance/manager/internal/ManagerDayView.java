package com.hls.attendance.manager.internal;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** One Manager's day as the screens see it. The location is never part of this view. */
public record ManagerDayView(
        UUID id,
        LocalDate date,
        String dayType,
        String purpose,
        Instant checkInAt,
        boolean late,
        Instant checkOutAt,
        boolean hasPhoto,
        Leave leave,
        List<String> flags,
        List<ActivityRef> activities) {

    /** A planned activity linked to a Field day, by kind and id. */
    public record ActivityRef(String kind, UUID id) {}

    /** Approved leave on the day: 1.0 or 0.5, and whether it is unpaid (Loss of Pay). */
    public record Leave(BigDecimal fraction, boolean unpaid) {}

    /** {@code today} is the business date; a past day with a check-in and no check-out is flagged. */
    static ManagerDayView of(ManagerDay day, LocalDate today) {
        List<String> flags = new ArrayList<>();
        if (day.isLate()) {
            flags.add("LATE");
        }
        if (day.hasCheckIn() && day.getCheckOutAt() == null && day.getDayDate().isBefore(today)) {
            flags.add("NO_CHECK_OUT");
        }
        if (day.isAddedLater()) {
            flags.add("ADDED_LATER");
        }
        if (day.isCorrected()) {
            flags.add("CORRECTED");
        }
        if (day.isUnlinked()) {
            flags.add("UNLINKED");
        }
        Leave leave = day.hasLeave() ? new Leave(day.getLeaveFraction(), day.isLeaveUnpaid()) : null;
        return new ManagerDayView(
                day.getId(),
                day.getDayDate(),
                day.getDayType() == null ? null : day.getDayType().name(),
                day.getPurpose(),
                day.getCheckInAt(),
                day.isLate(),
                day.getCheckOutAt(),
                day.getPhotoFileId() != null,
                leave,
                List.copyOf(flags),
                day.getActivities().stream()
                        .map(a -> new ActivityRef(a.getKind(), a.getActivityId()))
                        .sorted(java.util.Comparator.comparing(ActivityRef::kind).thenComparing(ActivityRef::id))
                        .toList());
    }
}
