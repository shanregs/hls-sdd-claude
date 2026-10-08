package com.hls.teacher.internal;

import com.hls.designation.api.DesignationDirectory;
import com.hls.designation.api.DesignationDirectory.Kind;
import com.hls.identity.user.AppUser;
import com.hls.identity.user.AppUserRepository;
import com.hls.identity.user.Role;
import com.hls.school.api.SchoolDirectory;
import com.hls.school.api.SchoolDirectory.SchoolInfo;
import com.hls.teacher.api.TeacherPlacementSource;
import java.time.LocalDate;
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
 * Dev-only demo data (inert unless {@code hls.seed.demo-data=true}, idempotent): the demo Teacher
 * (Tara, linked to her account) placed in the first demo School, three more placed Teachers and one
 * unplaced Teacher.
 */
@Component
@Order(40)
@ConditionalOnProperty(name = "hls.seed.demo-data", havingValue = "true")
public class TeacherDevSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(TeacherDevSeeder.class);

    private static final int DEMO_HISTORY_DAYS = 75;

    private final TeacherService teacherService;
    private final TeacherPlacementSource placementSource;
    private final TeacherRepository teacherRepository;
    private final SchoolDirectory schoolDirectory;
    private final AppUserRepository appUserRepository;
    private final TeacherEmploymentService employment;
    private final DesignationDirectory designations;

    public TeacherDevSeeder(
            TeacherService teacherService,
            TeacherPlacementSource placementSource,
            TeacherRepository teacherRepository,
            SchoolDirectory schoolDirectory,
            AppUserRepository appUserRepository,
            TeacherEmploymentService employment,
            DesignationDirectory designations) {
        this.teacherService = teacherService;
        this.placementSource = placementSource;
        this.teacherRepository = teacherRepository;
        this.schoolDirectory = schoolDirectory;
        this.appUserRepository = appUserRepository;
        this.employment = employment;
        this.designations = designations;
    }

    @Override
    public void run(ApplicationArguments args) {
        AppUser admin = appUserRepository.findByPhone("9800000001").orElse(null);
        AppUser tara = appUserRepository.findByPhone("9800000004").orElse(null);
        var zone = schoolDirectory.zoneByName("Demo Zone");
        if (admin == null || tara == null || zone.isEmpty()) {
            return;
        }
        List<SchoolInfo> schools = schoolDirectory.schoolsInZone(zone.get().id());
        // Placed well in the past so the attendance demo has months of history to show.
        LocalDate placedFrom = LocalDate.now().minusDays(DEMO_HISTORY_DAYS);

        if (teacherRepository.findByUserId(tara.getId()).isEmpty()) {
            var teacher = teacherService.create(
                    admin.getId(),
                    new TeacherService.NewTeacher(
                            "Tara Teacher", "9800000004", "tara@example.com", "Demo address", TeacherStatus.ACTIVE, tara.getId()));
            teacherService.create(
                    admin.getId(),
                    new TeacherService.NewTeacher("Unplaced Teacher", "9800000010", null, null, TeacherStatus.IN_TRAINING, null));
            if (!schools.isEmpty()) {
                placementSource.assign(admin.getId(), teacher.id(), schools.get(0).id(), null, placedFrom);
            }
            log.info("[DEV SEED] Teacher demo data ready: Tara (linked) placed in the first demo School.");
        }

        // More Teachers so the attendance grids have something to show (School index: 0 = One, 1 = Two).
        String[][] extras = {
            {"Meena Selvi", "9800000011", "0"},
            {"Karthik Raja", "9800000012", "0"},
            {"Lakshmi Priya", "9800000013", "1"},
        };
        for (String[] extra : extras) {
            int schoolIndex = Integer.parseInt(extra[2]);
            if (teacherRepository.findFirstByPhone(extra[1]).isPresent() || schools.size() <= schoolIndex) {
                continue;
            }
            var created = teacherService.create(
                    admin.getId(),
                    new TeacherService.NewTeacher(extra[0], extra[1], null, null, TeacherStatus.ACTIVE, null));
            placementSource.assign(admin.getId(), created.id(), schools.get(schoolIndex).id(), null, placedFrom);
        }
        seedEmployment(admin);
    }

    /**
     * Spec 005a: Tara, Meena and Karthik get a designation and an employee id; Lakshmi and the unplaced Teacher are
     * left without so the "missing designation" counts and filter have something to show.
     */
    private void seedEmployment(AppUser admin) {
        String[][] people = {
            {"9800000004", "Primary Teacher", "HLS-T-001"},
            {"9800000011", "Secondary Teacher", "HLS-T-002"},
            {"9800000012", "Senior Teacher", "HLS-T-003"},
        };
        for (String[] person : people) {
            teacherRepository.findFirstByPhone(person[0]).ifPresent(teacher -> {
                var designation = designations.findByName(Kind.TEACHER, person[1]);
                if (teacher.getDesignationId() == null && designation.isPresent()) {
                    employment.update(
                            admin.getId(),
                            Set.of(Role.ADMIN),
                            teacher.getId(),
                            designation.get().id(),
                            person[2],
                            teacher.getVersion());
                }
            });
        }
    }
}
