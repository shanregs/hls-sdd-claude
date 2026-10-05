package com.hls.teacher.internal;

import com.hls.identity.user.Role;
import com.hls.school.api.ChangeRecorder;
import com.hls.school.api.ConflictException;
import com.hls.school.api.NotFoundException;
import com.hls.teacher.api.TeacherRegistry;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** {@link TeacherRegistry} over {@link TeacherService} and {@link SalaryService}, acting with the rights of an Admin. */
@Service
class TeacherRegistryImpl implements TeacherRegistry {

    private static final Set<Role> ADMIN = Set.of(Role.ADMIN);

    private final TeacherService teacherService;
    private final SalaryService salaryService;
    private final TeacherRepository teachers;
    private final SalaryHistoryRepository salaries;
    private final ChangeRecorder changes;

    TeacherRegistryImpl(
            TeacherService teacherService,
            SalaryService salaryService,
            TeacherRepository teachers,
            SalaryHistoryRepository salaries,
            ChangeRecorder changes) {
        this.teacherService = teacherService;
        this.salaryService = salaryService;
        this.teachers = teachers;
        this.salaries = salaries;
        this.changes = changes;
    }

    /** The last ten digits of the phone, or null when it has none. */
    static String phoneKey(String phone) {
        if (phone == null) {
            return null;
        }
        String digits = phone.replaceAll("\\D", "");
        if (digits.isEmpty()) {
            return null;
        }
        return digits.length() > 10 ? digits.substring(digits.length() - 10) : digits;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Match> findMatches(String phone, String email) {
        String key = phoneKey(phone);
        String mail = email == null || email.isBlank() ? null : email.trim().toLowerCase();
        if (key == null && mail == null) {
            return List.of();
        }
        return teachers.findMatching(key, mail).stream()
                .map(t -> new Match(t.getId(), t.getName(), t.getStatus().name()))
                .toList();
    }

    @Override
    @Transactional
    public UUID createTrainee(UUID actor, Candidate details) {
        return teacherService
                .create(
                        actor,
                        new TeacherService.NewTeacher(
                                details.name(), details.phone(), details.email(), details.address(), TeacherStatus.IN_TRAINING, null))
                .id();
    }

    @Override
    @Transactional
    public void activate(UUID actor, UUID teacherId) {
        Teacher teacher = teachers.findById(teacherId).orElseThrow(() -> new NotFoundException("Teacher not found."));
        if (teacher.getStatus() != TeacherStatus.IN_TRAINING) {
            throw new ConflictException("Only a Teacher in training can be activated.");
        }
        teacherService.changeStatus(actor, teacherId, TeacherStatus.ACTIVE, LocalDate.now());
    }

    @Override
    @Transactional
    public void exit(UUID actor, UUID teacherId, LocalDate on, String reason) {
        teacherService.changeStatus(actor, teacherId, TeacherStatus.EXITED, on);
        if (reason != null && !reason.isBlank()) {
            changes.record(actor, "TEACHER", teacherId, "exitReason", null, reason.trim());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasSalaryEntry(UUID teacherId) {
        return salaries.existsByTeacherId(teacherId);
    }

    @Override
    @Transactional
    public void recordFirstSalary(UUID actor, UUID teacherId, BigDecimal amount, LocalDate effectiveOn) {
        if (salaries.existsByTeacherId(teacherId)) {
            throw new ConflictException("This Teacher already has a salary entry.");
        }
        salaryService.record(actor, ADMIN, teacherId, amount, effectiveOn);
    }

    @Override
    @Transactional(readOnly = true)
    public Set<UUID> activeTeacherIds() {
        return Set.copyOf(teachers.findIdsByStatus(TeacherStatus.ACTIVE));
    }
}
