package com.hls.teacher.internal;

import com.hls.identity.user.AppUser;
import com.hls.identity.user.AppUserRepository;
import com.hls.school.api.SchoolDirectory;
import com.hls.school.api.SchoolDirectory.SchoolInfo;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Dev-only demo data (inert unless {@code hls.seed.demo-data=true}, idempotent): the demo Teacher
 * (Tara, linked to her account) placed in the first demo School, plus one unplaced Teacher.
 */
@Component
@Order(40)
@ConditionalOnProperty(name = "hls.seed.demo-data", havingValue = "true")
public class TeacherDevSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(TeacherDevSeeder.class);

    private final TeacherService teacherService;
    private final TeacherPlacementService placementService;
    private final TeacherRepository teacherRepository;
    private final SchoolDirectory schoolDirectory;
    private final AppUserRepository appUserRepository;

    public TeacherDevSeeder(
            TeacherService teacherService,
            TeacherPlacementService placementService,
            TeacherRepository teacherRepository,
            SchoolDirectory schoolDirectory,
            AppUserRepository appUserRepository) {
        this.teacherService = teacherService;
        this.placementService = placementService;
        this.teacherRepository = teacherRepository;
        this.schoolDirectory = schoolDirectory;
        this.appUserRepository = appUserRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        AppUser admin = appUserRepository.findByPhone("9800000001").orElse(null);
        AppUser tara = appUserRepository.findByPhone("9800000004").orElse(null);
        var zone = schoolDirectory.zoneByName("Demo Zone");
        if (admin == null || tara == null || zone.isEmpty() || teacherRepository.findByUserId(tara.getId()).isPresent()) {
            return;
        }
        var teacher = teacherService.create(
                admin.getId(),
                new TeacherService.NewTeacher(
                        "Tara Teacher", "9800000004", "tara@example.com", "Demo address", TeacherStatus.ACTIVE, tara.getId()));
        teacherService.create(
                admin.getId(),
                new TeacherService.NewTeacher("Unplaced Teacher", "9800000010", null, null, TeacherStatus.IN_TRAINING, null));
        List<SchoolInfo> schools = schoolDirectory.schoolsInZone(zone.get().id());
        if (!schools.isEmpty()) {
            placementService.place(admin.getId(), teacher.id(), schools.get(0).id(), null);
        }
        log.info("[DEV SEED] Teacher demo data ready: Tara (linked) placed in the first demo School.");
    }
}
