package com.hls.recruitment.marketing.internal;

import com.hls.reporting.api.ManagerColumns;
import com.hls.reporting.api.TileContext;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Visits missed (planned in the chosen month, date already passed, the Manager among the attendees) and Schools won (the
 * prospects the Manager owns that are won) per Manager, in two queries however many Managers there are. Spec 014c A8 adds
 * visits done (completed, dated in the month) and Schools won in the month (won date in the business time zone); the two
 * existing figures are unchanged.
 */
@Component
class MarketingManagerColumns implements ManagerColumns {

    private final MarketingActivityRepository activities;
    private final ProspectRepository prospects;
    private final Clock clock;
    private final ZoneId zone;

    MarketingManagerColumns(
            MarketingActivityRepository activities,
            ProspectRepository prospects,
            Clock clock,
            @Value("${hls.business-timezone:Asia/Kolkata}") String zoneId) {
        this.activities = activities;
        this.prospects = prospects;
        this.clock = clock;
        this.zone = ZoneId.of(zoneId);
    }

    @Override
    public List<Column> columns() {
        return List.of(
                new Column("marketing.visitsMissed", "Visits missed", "/marketing/calendar?status=missed&attendeeUserId={managerId}"),
                new Column("marketing.won", "Schools won", "/marketing/prospects?won=true&ownerUserId={managerId}"),
                new Column("marketing.visitsDone", "Visits done", "/marketing/calendar?status=completed&attendeeUserId={managerId}"),
                new Column("marketing.wonInMonth", "Schools won in month", "/marketing/prospects?won=true&ownerUserId={managerId}"));
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, Map<String, Long>> figures(TileContext ctx, Set<UUID> managerUserIds) {
        LocalDate today = LocalDate.now(clock);
        YearMonth month = ctx.month();
        Map<UUID, long[]> counts = new HashMap<>();
        for (MarketingActivity a : activities.findByActivityDateBetweenOrderByActivityDate(month.atDay(1), month.atEndOfMonth())) {
            boolean missed = a.getStatus() == ActivityStatus.PLANNED && a.getActivityDate().isBefore(today);
            boolean done = a.getStatus() == ActivityStatus.COMPLETED;
            if (missed || done) {
                for (UUID attendee : a.getAttendees()) {
                    if (managerUserIds.contains(attendee)) {
                        counts.computeIfAbsent(attendee, k -> new long[4])[missed ? 0 : 2]++;
                    }
                }
            }
        }
        for (Prospect p : prospects.findAll()) {
            if (p.isWon() && p.getOwnerUserId() != null && managerUserIds.contains(p.getOwnerUserId())) {
                long[] c = counts.computeIfAbsent(p.getOwnerUserId(), k -> new long[4]);
                c[1]++;
                if (YearMonth.from(p.getWonAt().atZone(zone)).equals(month)) {
                    c[3]++;
                }
            }
        }
        Map<UUID, Map<String, Long>> result = new HashMap<>();
        for (UUID id : managerUserIds) {
            long[] c = counts.getOrDefault(id, new long[4]);
            result.put(id, Map.of(
                    "marketing.visitsMissed", c[0],
                    "marketing.won", c[1],
                    "marketing.visitsDone", c[2],
                    "marketing.wonInMonth", c[3]));
        }
        return result;
    }
}
