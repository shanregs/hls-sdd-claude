package com.hls.incentive.internal;

import com.hls.incentive.api.IncentiveSettings;
import com.hls.organization.api.ManagerQueries;
import com.hls.organization.api.ManagerQueries.ManagerRef;
import com.hls.school.api.SchoolDirectory;
import com.hls.school.api.SchoolDirectory.SchoolInfo;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Dev-only demo data (inert unless {@code hls.seed.demo-data=true}, idempotent): one incentive in each status for the Manager
 * of the demo Schools, and one School in each of two no-earner reasons, so the Incentives screen opens with something to see.
 * It writes only this module's own tables; the amount is the configured one.
 */
@Component
@Order(70)
@ConditionalOnProperty(name = "hls.seed.demo-data", havingValue = "true")
public class IncentiveDevSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(IncentiveDevSeeder.class);

    /** Status given to each demo School in turn; the last two Schools show as having earned nobody anything. */
    private static final List<String> STATUSES = List.of("PENDING_APPROVAL", "APPROVED", "REJECTED", "HELD");

    private static final List<String> NO_EARNER_REASONS = List.of("NO_PROSPECT", "OWNER_NOT_MANAGER");

    private final JdbcTemplate jdbc;
    private final SchoolDirectory schools;
    private final ManagerQueries managers;
    private final IncentiveSettings settings;
    private final Clock clock;

    public IncentiveDevSeeder(
            JdbcTemplate jdbc, SchoolDirectory schools, ManagerQueries managers, IncentiveSettings settings, Clock clock) {
        this.jdbc = jdbc;
        this.schools = schools;
        this.managers = managers;
        this.settings = settings;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            seed();
        } catch (RuntimeException e) {
            log.warn("[DEV SEED] The demo incentives could not be seeded: {}", e.getMessage());
        }
    }

    private void seed() {
        var zone = schools.zoneByName("Demo Zone");
        if (zone.isEmpty()) {
            return;
        }
        List<SchoolInfo> demoSchools = schools.schoolsInZone(zone.get().id());
        if (demoSchools.isEmpty() || alreadySeeded(demoSchools)) {
            return;
        }
        Optional<ManagerRef> manager = managers.managerOfSchool(demoSchools.get(0).id());
        if (manager.isEmpty() || manager.get().userId() == null) {
            return;
        }
        Instant now = clock.instant();
        BigDecimal amount = BigDecimal.valueOf(settings.amountPerSchool());
        if (amount.signum() <= 0) {
            return;
        }
        int withIncentive = Math.min(STATUSES.size(), demoSchools.size());
        int withoutEarner = Math.min(NO_EARNER_REASONS.size(), demoSchools.size() - withIncentive);
        for (int i = 0; i < withIncentive; i++) {
            insertIncentive(demoSchools.get(i), zone.get().id(), manager.get(), amount, STATUSES.get(i), now);
        }
        for (int i = 0; i < withoutEarner; i++) {
            jdbc.update(
                    "insert into no_earner (id, school_id, reason, detail, created_at) values (?, ?, ?, ?, ?)",
                    UUID.randomUUID(),
                    demoSchools.get(withIncentive + i).id(),
                    NO_EARNER_REASONS.get(i),
                    "Demo data",
                    Timestamp.from(now));
        }
        log.info("[DEV SEED] Incentive demo data ready: {} incentives and {} no-earner Schools.", withIncentive, withoutEarner);
    }

    private boolean alreadySeeded(List<SchoolInfo> demoSchools) {
        UUID first = demoSchools.get(0).id();
        Integer found = jdbc.queryForObject(
                "select (select count(*) from incentive where school_id = ?) + (select count(*) from no_earner where school_id = ?)",
                Integer.class,
                first,
                first);
        return found != null && found > 0;
    }

    private void insertIncentive(
            SchoolInfo school, UUID zoneId, ManagerRef manager, BigDecimal amount, String status, Instant now) {
        UUID id = UUID.randomUUID();
        boolean decided = !"PENDING_APPROVAL".equals(status);
        jdbc.update(
                "insert into incentive (id, school_id, contract_id, earner_manager_id, earner_user_id, amount, closure_date, status,"
                        + " decided_at, reject_reason, hold_reason, zone_id, version, created_at, updated_at)"
                        + " values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?)",
                id,
                school.id(),
                UUID.randomUUID(),
                manager.id(),
                manager.userId(),
                amount,
                java.sql.Date.valueOf(LocalDate.now(clock).minusDays(3)),
                status,
                decided ? Timestamp.from(now) : null,
                "REJECTED".equals(status) ? "Demo: MoU figures do not match" : null,
                "HELD".equals(status) ? "MoU not approved" : null,
                zoneId,
                Timestamp.from(now),
                Timestamp.from(now));
        event(id, "EARNED", "Demo data", now);
        if ("APPROVED".equals(status) || "HELD".equals(status)) {
            event(id, "APPROVED", "Demo data", now);
        }
        if ("REJECTED".equals(status)) {
            event(id, "REJECTED", "Demo: MoU figures do not match", now);
        }
        if ("HELD".equals(status)) {
            event(id, "HELD", "MoU not approved", now);
        }
    }

    private void event(UUID incentiveId, String kind, String detail, Instant now) {
        jdbc.update(
                "insert into incentive_event (id, incentive_id, kind, detail, created_at) values (?, ?, ?, ?, ?)",
                UUID.randomUUID(),
                incentiveId,
                kind,
                detail,
                Timestamp.from(now));
    }
}
