package com.hls.recruitment.marketing.internal;

import com.hls.reporting.api.ManagerColumns;
import com.hls.reporting.api.TileContext;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Visits missed (planned in the chosen month, date already passed, the Manager among the attendees) and Schools won (the
 * prospects the Manager owns that are won) per Manager, in two queries however many Managers there are.
 */
@Component
class MarketingManagerColumns implements ManagerColumns {

    private final MarketingActivityRepository activities;
    private final ProspectRepository prospects;
    private final Clock clock;

    MarketingManagerColumns(MarketingActivityRepository activities, ProspectRepository prospects, Clock clock) {
        this.activities = activities;
        this.prospects = prospects;
        this.clock = clock;
    }

    @Override
    public List<Column> columns() {
        return List.of(
                new Column("marketing.visitsMissed", "Visits missed", "/marketing/calendar?status=missed&attendeeUserId={managerId}"),
                new Column("marketing.won", "Schools won", "/marketing/prospects?won=true&ownerUserId={managerId}"));
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, Map<String, Long>> figures(TileContext ctx, Set<UUID> managerUserIds) {
        LocalDate today = LocalDate.now(clock);
        YearMonth month = ctx.month();
        Map<UUID, long[]> counts = new HashMap<>();
        for (MarketingActivity a : activities.findByActivityDateBetweenOrderByActivityDate(month.atDay(1), month.atEndOfMonth())) {
            if (a.getStatus() == ActivityStatus.PLANNED && a.getActivityDate().isBefore(today)) {
                for (UUID attendee : a.getAttendees()) {
                    if (managerUserIds.contains(attendee)) {
                        counts.computeIfAbsent(attendee, k -> new long[2])[0]++;
                    }
                }
            }
        }
        for (Prospect p : prospects.findAll()) {
            if (p.isWon() && p.getOwnerUserId() != null && managerUserIds.contains(p.getOwnerUserId())) {
                counts.computeIfAbsent(p.getOwnerUserId(), k -> new long[2])[1]++;
            }
        }
        Map<UUID, Map<String, Long>> result = new HashMap<>();
        for (UUID id : managerUserIds) {
            long[] c = counts.getOrDefault(id, new long[2]);
            result.put(id, Map.of("marketing.visitsMissed", c[0], "marketing.won", c[1]));
        }
        return result;
    }
}
