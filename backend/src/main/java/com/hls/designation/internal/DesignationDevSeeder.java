package com.hls.designation.internal;

import com.hls.designation.api.DesignationDirectory.Kind;
import com.hls.identity.user.AppUser;
import com.hls.identity.user.AppUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Dev-only demo data (inert unless {@code hls.seed.demo-data=true}, idempotent): a few designations so the
 * Designations screen and the employment fields can be tried. The people are given designations by the Manager and
 * Teacher seeders, which run after this one.
 */
@Component
@Order(25)
@ConditionalOnProperty(name = "hls.seed.demo-data", havingValue = "true")
class DesignationDevSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DesignationDevSeeder.class);

    private final DesignationService designations;
    private final AppUserRepository users;

    DesignationDevSeeder(DesignationService designations, AppUserRepository users) {
        this.designations = designations;
        this.users = users;
    }

    @Override
    public void run(ApplicationArguments args) {
        AppUser admin = users.findByPhone("9800000001").orElse(null);
        if (admin == null) {
            return;
        }
        String[][] demo = {
            {"Primary Teacher", "TEACHER"},
            {"Secondary Teacher", "TEACHER"},
            {"Senior Teacher", "TEACHER"},
            {"Zone Manager", "MANAGER"},
            {"Senior Zone Manager", "MANAGER"},
        };
        int added = 0;
        for (String[] row : demo) {
            Kind kind = Kind.valueOf(row[1]);
            if (designations.findByName(kind, row[0]).isEmpty()) {
                designations.create(admin.getId(), row[0], kind);
                added++;
            }
        }
        if (added > 0) {
            log.info("[DEV SEED] Designations demo data ready: {} added.", added);
        }
    }
}
