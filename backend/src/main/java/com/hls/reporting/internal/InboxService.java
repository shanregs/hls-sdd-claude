package com.hls.reporting.internal;

import com.hls.identity.permissions.PermissionGuard;
import com.hls.identity.permissions.PermissionModule;
import com.hls.identity.user.Role;
import com.hls.reporting.api.ApprovalItem;
import com.hls.reporting.api.ApprovalSource;
import com.hls.reporting.api.Link;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The approvals waiting for the caller, merged from every {@link ApprovalSource} (spec 028 US6). A source is asked only when the
 * caller holds its grant, so a kind the caller may not decide is absent from the list and the totals. The caller's own requests
 * are dropped. Oldest first; the totals count every page.
 */
@Service
public class InboxService {

    public static final int DEFAULT_PAGE_SIZE = 50;
    public static final int MAX_PAGE_SIZE = 100;

    public record Item(String kind, UUID itemId, String requester, String subject, LocalDate askedOn, int ageDays, Link link) {}

    public record InboxResponse(Map<String, Long> totals, Instant takenAt, int page, int size, long total, List<Item> items) {}

    private final ObjectProvider<ApprovalSource> sources;
    private final PermissionGuard guard;
    private final ZoneResolver zones;
    private final Clock clock;

    public InboxService(ObjectProvider<ApprovalSource> sources, PermissionGuard guard, ZoneResolver zones, Clock clock) {
        this.sources = sources;
        this.guard = guard;
        this.zones = zones;
        this.clock = clock;
    }

    /** True when at least one source is granted to the caller; the manifest's {@code inbox} flag. */
    public boolean available(Set<Role> roles) {
        return !TileRegistry.managerOnly(roles) && !granted(roles).isEmpty();
    }

    private List<ApprovalSource> granted(Set<Role> roles) {
        return sources.orderedStream()
                .filter(s -> guard.isGranted(roles, PermissionModule.valueOf(s.permissionModule()), s.action()))
                .toList();
    }

    @Transactional(readOnly = true)
    public InboxResponse inbox(UUID userId, Set<Role> roles, String kind, int page, int size) {
        if (!available(roles)) {
            throw new DashboardForbiddenException("The inbox is for Admin and Director.");
        }
        List<ApprovalSource> allowed = granted(roles);
        if (kind != null && !kind.isBlank() && allowed.stream().noneMatch(s -> s.kind().equals(kind))) {
            throw new DashboardInvalidInputException("Unknown kind.");
        }
        LocalDate today = zones.today();
        Map<String, Long> totals = new LinkedHashMap<>();
        List<ApprovalItem> all = new ArrayList<>();
        for (ApprovalSource source : allowed) {
            List<ApprovalItem> items = source.pending(userId, roles).stream()
                    .filter(i -> !userId.equals(i.requesterUserId()))
                    .toList();
            totals.put(source.kind(), (long) items.size());
            if (kind == null || kind.isBlank() || kind.equals(source.kind())) {
                all.addAll(items);
            }
        }
        all.sort(Comparator.comparing(ApprovalItem::askedOn, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(i -> i.itemId().toString()));
        int pageSize = Math.min(Math.max(size <= 0 ? DEFAULT_PAGE_SIZE : size, 1), MAX_PAGE_SIZE);
        int from = (int) Math.min((long) Math.max(page, 0) * pageSize, all.size());
        int to = Math.min(from + pageSize, all.size());
        List<Item> content = all.subList(from, to).stream()
                .map(i -> new Item(i.kind(), i.itemId(), i.requesterName(), i.subject(), i.askedOn(),
                        businessDaysBetween(i.askedOn(), today), i.link()))
                .toList();
        return new InboxResponse(totals, Instant.now(clock), Math.max(page, 0), pageSize, all.size(), content);
    }

    /** Whole working days from the date the request was made up to today (Saturday and Sunday are not counted). */
    static int businessDaysBetween(LocalDate from, LocalDate to) {
        if (from == null || !from.isBefore(to)) {
            return 0;
        }
        int days = 0;
        for (LocalDate d = from; d.isBefore(to); d = d.plusDays(1)) {
            DayOfWeek w = d.getDayOfWeek();
            if (w != DayOfWeek.SATURDAY && w != DayOfWeek.SUNDAY) {
                days++;
            }
        }
        return days;
    }
}
