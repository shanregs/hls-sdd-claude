package com.hls.organization.internal;

import com.hls.identity.user.AppUser;
import com.hls.identity.user.AppUserRepository;
import com.hls.school.api.SchoolDirectory;
import com.hls.school.api.SchoolDirectory.SchoolInfo;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Dev-only demo data (inert unless {@code hls.seed.demo-data=true}, idempotent): a Manager record
 * for the demo Manager (Manoj) covering the demo Zone, with the first demo School assigned to him.
 */
@Component
@Order(30)
@ConditionalOnProperty(name = "hls.seed.demo-data", havingValue = "true")
public class OrganizationDevSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(OrganizationDevSeeder.class);

    private final ManagerService managerService;
    private final ManagerRepository managerRepository;
    private final SchoolDirectory schoolDirectory;
    private final AppUserRepository appUserRepository;

    public OrganizationDevSeeder(
            ManagerService managerService,
            ManagerRepository managerRepository,
            SchoolDirectory schoolDirectory,
            AppUserRepository appUserRepository) {
        this.managerService = managerService;
        this.managerRepository = managerRepository;
        this.schoolDirectory = schoolDirectory;
        this.appUserRepository = appUserRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        AppUser admin = appUserRepository.findByPhone("9800000001").orElse(null);
        AppUser manoj = appUserRepository.findByPhone("9800000003").orElse(null);
        var zone = schoolDirectory.zoneByName("Demo Zone");
        if (admin == null || manoj == null || zone.isEmpty() || managerRepository.findByUserId(manoj.getId()).isPresent()) {
            return;
        }
        UUID managerId = managerService.create(admin.getId(), manoj.getId()).id();
        var view = managerService.get(managerId);
        managerService.setZones(admin.getId(), managerId, List.of(zone.get().id()), view.version());
        List<SchoolInfo> schools = schoolDirectory.schoolsInZone(zone.get().id());
        if (!schools.isEmpty()) {
            managerService.assignSchoolManager(admin.getId(), schools.get(0).id(), managerId);
        }
        log.info("[DEV SEED] Manager demo data ready: Manoj covers Demo Zone and manages the first demo School.");
    }
}
