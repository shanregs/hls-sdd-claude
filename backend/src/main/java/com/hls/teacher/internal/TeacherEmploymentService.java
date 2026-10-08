package com.hls.teacher.internal;

import com.hls.designation.api.DesignationDirectory;
import com.hls.designation.api.DesignationDirectory.Kind;
import com.hls.designation.api.EmployeeIds;
import com.hls.designation.api.EmployeeIds.PersonKind;
import com.hls.identity.user.Role;
import com.hls.school.api.ChangeRecorder;
import com.hls.school.api.StaleVersion;
import com.hls.teacher.api.TeacherView;
import java.time.Clock;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A Teacher's designation and optional employee id (spec 005a US3). Only the current designation is kept; the audit
 * log keeps earlier ones. An exited Teacher can still be corrected.
 */
@Service
public class TeacherEmploymentService {

    private final TeacherRepository teacherRepository;
    private final TeacherService teacherService;
    private final DesignationDirectory designations;
    private final EmployeeIds employeeIds;
    private final ChangeRecorder changes;
    private final Clock clock;

    public TeacherEmploymentService(
            TeacherRepository teacherRepository,
            TeacherService teacherService,
            DesignationDirectory designations,
            EmployeeIds employeeIds,
            ChangeRecorder changes,
            Clock clock) {
        this.teacherRepository = teacherRepository;
        this.teacherService = teacherService;
        this.designations = designations;
        this.employeeIds = employeeIds;
        this.changes = changes;
        this.clock = clock;
    }

    /** Sets (or clears, with null) the designation and the employee id; the Teacher must be in the caller's scope. */
    @Transactional
    public TeacherView update(
            UUID actor, Set<Role> roles, UUID teacherId, UUID designationId, String employeeId, Long version) {
        Teacher teacher = teacherService.visible(actor, roles, teacherId);
        StaleVersion.check(Teacher.class, teacherId, teacher.getVersion(), version);
        UUID beforeDesignation = teacher.getDesignationId();
        if (designationId != null && !designationId.equals(beforeDesignation)) {
            designations.assign(designationId, Kind.TEACHER);
        }
        String newId = employeeIds.claim(PersonKind.TEACHER, teacherId, teacher.getName(), employeeId);
        changes.record(actor, "TEACHER", teacherId, "designation", nameOf(beforeDesignation), nameOf(designationId));
        changes.record(actor, "TEACHER", teacherId, "employeeId", teacher.getEmployeeId(), newId);
        teacher.setEmployment(designationId, newId, clock.instant());
        teacherRepository.saveAndFlush(teacher);
        return teacherService.viewsOf(List.of(teacher), null).get(0);
    }

    private String nameOf(UUID designationId) {
        if (designationId == null) {
            return null;
        }
        return designations.find(designationId).map(DesignationDirectory.DesignationInfo::name).orElse("?");
    }
}
