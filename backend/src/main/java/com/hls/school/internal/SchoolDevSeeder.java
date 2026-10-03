package com.hls.school.internal;

import com.hls.identity.user.AppUserRepository;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Dev-only demo data (inert unless {@code hls.seed.demo-data=true}, idempotent): one Zone, two
 * Places and two Schools, created as the demo Admin. Manager and Teacher demo data is seeded by
 * their own modules so no module depends on a module it should not know about.
 */
@Component
@Order(20)
@ConditionalOnProperty(name = "hls.seed.demo-data", havingValue = "true")
public class SchoolDevSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SchoolDevSeeder.class);
    public static final String ZONE_NAME = "Demo Zone";

    private final ZoneService zoneService;
    private final PlaceService placeService;
    private final SchoolService schoolService;
    private final ZoneRepository zoneRepository;
    private final PlaceRepository placeRepository;
    private final SchoolRepository schoolRepository;
    private final AppUserRepository appUserRepository;

    public SchoolDevSeeder(
            ZoneService zoneService,
            PlaceService placeService,
            SchoolService schoolService,
            ZoneRepository zoneRepository,
            PlaceRepository placeRepository,
            SchoolRepository schoolRepository,
            AppUserRepository appUserRepository) {
        this.zoneService = zoneService;
        this.placeService = placeService;
        this.schoolService = schoolService;
        this.zoneRepository = zoneRepository;
        this.placeRepository = placeRepository;
        this.schoolRepository = schoolRepository;
        this.appUserRepository = appUserRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (zoneRepository.findByNameIgnoreCase(ZONE_NAME).isPresent()) {
            return;
        }
        UUID admin = appUserRepository
                .findByPhone("9800000001")
                .map(u -> u.getId())
                .orElse(null);
        if (admin == null) {
            log.info("[DEV SEED] Demo Admin not found; skipping school demo data.");
            return;
        }
        UUID zoneId = zoneService.create(admin, ZONE_NAME).id();
        UUID madurantakam = placeService.add(admin, zoneId, "Madurantakam", "603306").id();
        UUID maraimalai = placeService.add(admin, zoneId, "Maraimalai Nagar", "603209").id();
        schoolService.create(
                admin,
                madurantakam,
                new SchoolService.Profile("Demo School One", "1 Main Road", "Head One", "9000000001", "one@example.com", null));
        schoolService.create(
                admin,
                maraimalai,
                new SchoolService.Profile("Demo School Two", "2 Station Road", "Head Two", "9000000002", "two@example.com", null));
        log.info(
                "[DEV SEED] School demo data ready: {} ({} places, {} schools).",
                ZONE_NAME,
                placeRepository.countByZoneId(zoneId),
                schoolRepository.countInZone(zoneId));
    }
}
