package com.hls.schoolbilling.internal;

import com.hls.identity.user.AppUser;
import com.hls.identity.user.AppUserRepository;
import com.hls.identity.user.Role;
import com.hls.school.api.SchoolDirectory;
import com.hls.school.api.SchoolDirectory.SchoolInfo;
import com.hls.schoolbilling.internal.ReceivablesDtos.BillingCaller;
import com.hls.schoolbilling.internal.ReceivablesDtos.CollectionBody;
import com.hls.schoolbilling.internal.ReceivablesDtos.RaiseResult;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Dev-only demo data (inert unless {@code hls.seed.demo-data=true}, idempotent): the monthly due of the first demo School for
 * this month (its Teachers are mapped by {@link ContractDevSeeder}) with one part collection, so School Billing opens with
 * something to see.
 */
@Component
@Order(46)
@ConditionalOnProperty(name = "hls.seed.demo-data", havingValue = "true")
public class BillingDevSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BillingDevSeeder.class);

    private final AppUserRepository users;
    private final SchoolDirectory schools;
    private final DueService dues;
    private final CollectionService collections;
    private final Clock clock;

    public BillingDevSeeder(
            AppUserRepository users, SchoolDirectory schools, DueService dues, CollectionService collections, Clock clock) {
        this.users = users;
        this.schools = schools;
        this.dues = dues;
        this.collections = collections;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            seed();
        } catch (RuntimeException e) {
            log.warn("[DEV SEED] The demo monthly due could not be seeded: {}", e.getMessage());
        }
    }

    private void seed() {
        AppUser admin = users.findByPhone("9800000001").orElse(null);
        var zone = schools.zoneByName("Demo Zone");
        if (admin == null || zone.isEmpty()) {
            return;
        }
        List<SchoolInfo> demoSchools = schools.schoolsInZone(zone.get().id());
        if (demoSchools.isEmpty()) {
            return;
        }
        SchoolInfo first = demoSchools.get(0);
        BillingCaller caller = new BillingCaller(admin.getId(), Set.of(Role.ADMIN));
        RaiseResult result = dues.raiseAll(caller, YearMonth.from(LocalDate.now(clock)), List.of(first.id()));
        if (result.raised().isEmpty()) {
            return;
        }
        collections.record(
                caller,
                new CollectionBody(
                        first.id(), new BigDecimal("20000.00"), LocalDate.now(clock), "BANK", "DEMO-UTR-1", "Accounts", "Demo part payment", null, true));
        log.info("[DEV SEED] School billing demo data ready: {} has this month's due and one part collection.", first.name());
    }
}
