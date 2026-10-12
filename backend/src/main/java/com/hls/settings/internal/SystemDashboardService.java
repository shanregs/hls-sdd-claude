package com.hls.settings.internal;

import com.hls.audit.api.RecentAudit;
import com.hls.identity.user.AppUser;
import com.hls.identity.user.AppUserRepository;
import com.hls.identity.user.Role;
import com.hls.identity.user.RoleAssignmentRepository;
import java.lang.management.ManagementFactory;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** The System dashboard read model (spec 011 US1): health, user counts and the newest audit events. Stores nothing. */
@Service
public class SystemDashboardService {

    private static final Logger log = LoggerFactory.getLogger(SystemDashboardService.class);
    private static final int RECENT_EVENTS = 10;

    public record Health(
            String database,
            String applicationVersion,
            String migrationVersion,
            Instant startedAt,
            long uptimeSeconds,
            Instant serverTime) {}

    public record Users(long total, long active, long inactive, long lockedNow, Map<String, Long> byRole) {}

    public record AuditEvent(String kind, Instant at, String actorName, String summary) {}

    public record AuditPanel(String status, List<AuditEvent> events) {}

    public record Dashboard(Health health, Users users, long failedSignInsLast24h, AuditPanel recentAudit) {}

    private final JdbcTemplate jdbc;
    private final AppUserRepository users;
    private final RoleAssignmentRepository roleAssignments;
    private final RecentAudit audit;
    private final Clock clock;
    private final String applicationVersion;

    SystemDashboardService(
            JdbcTemplate jdbc,
            AppUserRepository users,
            RoleAssignmentRepository roleAssignments,
            RecentAudit audit,
            Clock clock,
            @Value("${hls.app.version:dev}") String applicationVersion) {
        this.jdbc = jdbc;
        this.users = users;
        this.roleAssignments = roleAssignments;
        this.audit = audit;
        this.clock = clock;
        this.applicationVersion = applicationVersion;
    }

    public Dashboard dashboard(Set<Role> callerRoles) {
        Instant now = clock.instant();
        Health health = health(now);
        Users counts = "UP".equals(health.database()) ? users(now) : new Users(0, 0, 0, 0, Map.of());
        long failed = 0;
        AuditPanel panel;
        try {
            failed = audit.failedSignInsSince(now.minus(Duration.ofHours(24)));
            List<RecentAudit.Event> events = audit.recent(callerRoles, RECENT_EVENTS);
            Map<UUID, String> names = names(events);
            panel = new AuditPanel(
                    "OK",
                    events.stream()
                            .map(e -> new AuditEvent(
                                    e.kind(),
                                    e.at(),
                                    e.actorUserId() == null ? null : names.getOrDefault(e.actorUserId(), "Unknown user"),
                                    e.summary()))
                            .toList());
        } catch (RuntimeException e) {
            log.warn("The recent audit lookup failed", e);
            panel = new AuditPanel("UNAVAILABLE", List.of());
        }
        return new Dashboard(health, counts, failed, panel);
    }

    private Health health(Instant now) {
        String database = "DOWN";
        String migration = null;
        try {
            jdbc.queryForObject("select 1", Integer.class);
            database = "UP";
            migration = jdbc.queryForObject(
                    "select version from flyway_schema_history where success and version is not null "
                            + "order by installed_rank desc limit 1",
                    String.class);
        } catch (RuntimeException e) {
            log.warn("A health lookup failed", e);
        }
        Instant started = Instant.ofEpochMilli(ManagementFactory.getRuntimeMXBean().getStartTime());
        return new Health(
                database, applicationVersion, migration, started, Math.max(0, Duration.between(started, now).toSeconds()), now);
    }

    private Users users(Instant now) {
        long total = users.count();
        long active = users.countByActive(true);
        Map<String, Long> byRole = new LinkedHashMap<>();
        for (Role role : Role.values()) {
            byRole.put(role.name(), roleAssignments.countByRole(role));
        }
        return new Users(total, active, total - active, users.countByLockUntilAfter(now), byRole);
    }

    private Map<UUID, String> names(List<RecentAudit.Event> events) {
        Set<UUID> ids = events.stream().map(RecentAudit.Event::actorUserId).filter(Objects::nonNull).collect(Collectors.toSet());
        return ids.isEmpty()
                ? Map.of()
                : users.findAllById(ids).stream().collect(Collectors.toMap(AppUser::getId, AppUser::getDisplayName));
    }
}
