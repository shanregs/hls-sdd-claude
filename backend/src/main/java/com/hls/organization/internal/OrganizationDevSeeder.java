package com.hls.organization.internal;

import com.hls.designation.api.DesignationDirectory;
import com.hls.designation.api.DesignationDirectory.Kind;
import com.hls.identity.user.AppUser;
import com.hls.identity.user.AppUserRepository;
import com.hls.school.api.SchoolDirectory;
import com.hls.school.api.SchoolDirectory.SchoolInfo;
import java.time.LocalDate;
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
    private final ManagerEmploymentService employment;
    private final DesignationDirectory designations;

    public OrganizationDevSeeder(
            ManagerService managerService,
            ManagerRepository managerRepository,
            SchoolDirectory schoolDirectory,
            AppUserRepository appUserRepository,
            ManagerEmploymentService employment,
            DesignationDirectory designations) {
        this.managerService = managerService;
        this.managerRepository = managerRepository;
        this.schoolDirectory = schoolDirectory;
        this.appUserRepository = appUserRepository;
        this.employment = employment;
        this.designations = designations;
    }

    @Override
    public void run(ApplicationArguments args) {
        AppUser admin = appUserRepository.findByPhone("9800000001").orElse(null);
        AppUser manoj = appUserRepository.findByPhone("9800000003").orElse(null);
        var zone = schoolDirectory.zoneByName("Demo Zone");
        if (admin == null || manoj == null || zone.isEmpty()) {
            return;
        }
        if (managerRepository.findByUserId(manoj.getId()).isPresent()) {
            seedEmployment(admin, managerRepository.findByUserId(manoj.getId()).get());
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
        seedEmployment(admin, managerRepository.findByUserId(manoj.getId()).orElseThrow());
    }

    /** Spec 005a: Manoj gets a designation, an employee id and a joining date, so the new fields have something to show. */
    private void seedEmployment(AppUser admin, Manager manoj) {
        if (manoj.getJoiningDate() != null) {
            return;
        }
        LocalDate joining = LocalDate.now().minusDays(400);
        employment.updateEmployment(admin.getId(), manoj.getId(), "HLS-M-001", joining, null, manoj.getVersion());
        designations
                .findByName(Kind.MANAGER, "Zone Manager")
                .ifPresent(d -> employment.appendDesignation(admin.getId(), manoj.getId(), d.id(), joining));
    }
}
