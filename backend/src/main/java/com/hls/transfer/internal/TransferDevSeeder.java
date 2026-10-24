package com.hls.transfer.internal;

import com.hls.designation.api.BusinessDate;
import com.hls.identity.user.Role;
import com.hls.identity.user.RoleAssignmentRepository;
import com.hls.school.api.SchoolDirectory;
import com.hls.school.api.SchoolDirectory.SchoolInfo;
import com.hls.teacher.api.TeacherDirectory;
import com.hls.teacher.api.TeacherDirectory.TeacherInfo;
import com.hls.teacher.api.TeacherPlacementSource;
import com.hls.teacher.api.TeacherPlacementSource.Assignment;
import com.hls.transfer.internal.MovementService.TransferBody;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Dev-only demo data (inert unless {@code hls.seed.demo-data=true}, idempotent): one Pending transfer of a placed Teacher to
 * another School, raised by an Admin, so the Transfers list and the Director approval have something to show. The wider
 * walkthrough data (two Managers, an unplaced Teacher, a School with a vacancy) comes from the other seeders.
 */
@Component
@Order(48)
@ConditionalOnProperty(name = "hls.seed.demo-data", havingValue = "true")
class TransferDevSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(TransferDevSeeder.class);

    private final MovementService service;
    private final MovementRequestRepository requests;
    private final RoleAssignmentRepository roles;
    private final TeacherDirectory teachers;
    private final TeacherPlacementSource placements;
    private final SchoolDirectory schools;
    private final BusinessDate businessDate;

    TransferDevSeeder(
            MovementService service,
            MovementRequestRepository requests,
            RoleAssignmentRepository roles,
            TeacherDirectory teachers,
            TeacherPlacementSource placements,
            SchoolDirectory schools,
            BusinessDate businessDate) {
        this.service = service;
        this.requests = requests;
        this.roles = roles;
        this.teachers = teachers;
        this.placements = placements;
        this.schools = schools;
        this.businessDate = businessDate;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (requests.count() > 0) {
            return;
        }
        UUID admin = roles.userIdsWithRole(Role.ADMIN).stream().findFirst().orElse(null);
        if (admin == null) {
            return;
        }
        List<SchoolInfo> active = schools.allSchools().stream().filter(SchoolInfo::active).toList();
        if (active.size() < 2) {
            return;
        }
        for (TeacherInfo teacher : teachers.activeTeachers()) {
            Assignment current = placements.currentOf(List.of(teacher.id()), businessDate.today()).get(teacher.id());
            if (current == null) {
                continue;
            }
            SchoolInfo target = active.stream().filter(s -> !s.id().equals(current.schoolId())).findFirst().orElse(null);
            if (target == null) {
                return;
            }
            try {
                service.raiseTransfer(
                        new Caller(admin, Set.of(Role.ADMIN), true, false),
                        new TransferBody(teacher.id(), target.id(), null, businessDate.today().plusDays(7), "MANAGER_REBALANCING", null));
                log.info("Seeded a Pending transfer for {}", teacher.name());
                return;
            } catch (RuntimeException e) {
                log.info("Skipped transfer demo data for {}: {}", teacher.name(), e.getMessage());
            }
        }
        log.info("No placed Teacher found for the transfer demo data {}", Map.of());
    }
}
