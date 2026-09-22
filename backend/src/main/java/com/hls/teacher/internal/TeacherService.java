package com.hls.teacher.internal;

import com.hls.audit.api.AuditWriter;
import com.hls.audit.api.dto.AuditAction;
import com.hls.audit.api.dto.AuditRecordRequest;
import com.hls.teacher.api.TeacherCommands;
import com.hls.teacher.api.TeacherQueries;
import com.hls.teacher.api.TeacherSalaryCommands;
import com.hls.teacher.api.TeacherSalaryQueries;
import com.hls.teacher.api.dto.CreateTeacherProfileRequest;
import com.hls.teacher.api.dto.SalaryAsOfAnswer;
import com.hls.teacher.api.dto.TeacherProfileView;
import com.hls.teacher.api.dto.TeacherSalaryHistoryView;
import com.hls.teacher.api.dto.TeacherStatus;
import com.hls.teacher.api.dto.UpdateTeacherProfileRequest;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implements {@link TeacherQueries}/{@link TeacherCommands} and, since
 * specs/009-teacher-salary-history, {@link TeacherSalaryQueries}/
 * {@link TeacherSalaryCommands} too — one class, mirroring {@code ZoneService}'s/
 * {@code PlaceService}'s own reasoning. Every write calls
 * {@code audit.api.AuditWriter.record(...)} inside its own transaction
 * (specs/005 FR-005) — {@code teacher} is Audit's first real caller.
 */
@Service
public class TeacherService implements TeacherQueries, TeacherCommands, TeacherSalaryQueries, TeacherSalaryCommands {

    private static final String ENTITY_TYPE = "TeacherProfile";

    private final TeacherProfileRepository teacherProfileRepository;
    private final TeacherSalaryHistoryRepository teacherSalaryHistoryRepository;
    private final AuditWriter auditWriter;
    private final Clock clock;

    public TeacherService(
            TeacherProfileRepository teacherProfileRepository,
            TeacherSalaryHistoryRepository teacherSalaryHistoryRepository,
            AuditWriter auditWriter,
            Clock clock) {
        this.teacherProfileRepository = teacherProfileRepository;
        this.teacherSalaryHistoryRepository = teacherSalaryHistoryRepository;
        this.auditWriter = auditWriter;
        this.clock = clock;
    }

    // ---- Queries -----------------------------------------------------------------------

    @Override
    public Optional<TeacherProfileView> findById(UUID teacherId) {
        return teacherProfileRepository.findById(teacherId).map(this::toView);
    }

    @Override
    public boolean exists(UUID teacherId) {
        return teacherProfileRepository.findById(teacherId).isPresent();
    }

    @Override
    public List<TeacherProfileView> findAll() {
        return teacherProfileRepository.findAll().stream().map(this::toView).toList();
    }

    // ---- Salary queries (specs/009-teacher-salary-history) -----------------------------

    @Override
    public SalaryAsOfAnswer currentSalary(UUID teacherId) {
        return salaryAsOf(teacherId, LocalDate.now(clock));
    }

    @Override
    public SalaryAsOfAnswer salaryAsOf(UUID teacherId, LocalDate date) {
        return teacherSalaryHistoryRepository.findByTeacherIdOrderByEffectiveFromDescCreatedAtDesc(teacherId).stream()
                .filter(entry -> !entry.getEffectiveFrom().isAfter(date))
                .findFirst()
                .map(entry -> SalaryAsOfAnswer.recorded(entry.getAmount()))
                .orElseGet(SalaryAsOfAnswer::notYetRecorded);
    }

    // ---- Commands ----------------------------------------------------------------------

    @Override
    @Transactional
    public TeacherProfileView create(CreateTeacherProfileRequest request, UUID actingUserId) {
        TeacherProfile profile = new TeacherProfile(
                UUID.randomUUID(), request.name(), request.phone(), request.email(),
                request.status(), clock.instant(), actingUserId);
        teacherProfileRepository.save(profile);

        // FR-002 (specs/009): the initial salary becomes the first history entry.
        TeacherSalaryHistory firstEntry = new TeacherSalaryHistory(
                UUID.randomUUID(), profile.getId(), request.hlsOfferedSalary(), LocalDate.now(clock), clock.instant(), actingUserId);
        teacherSalaryHistoryRepository.save(firstEntry);

        auditWriter.record(new AuditRecordRequest(
                "teacher", ENTITY_TYPE, profile.getId().toString(), AuditAction.CREATED,
                "Teacher profile created", null, describe(profile, request.hlsOfferedSalary()), actingUserId, "ADMIN",
                com.hls.CorrelationIdFilter.currentCorrelationId()));

        // Built directly from the request's salary, not re-queried — we already know it
        // (also avoids depending on the just-written row being visible to a follow-up read).
        return new TeacherProfileView(
                profile.getId(), profile.getName(), profile.getPhone(), profile.getEmail(),
                request.hlsOfferedSalary(), profile.getStatus(), profile.getCreatedAt());
    }

    @Override
    @Transactional
    public TeacherProfileView updateProfile(UUID teacherId, UpdateTeacherProfileRequest request, UUID actingUserId) {
        TeacherProfile profile = teacherProfileRepository.findById(teacherId)
                .orElseThrow(() -> new TeacherNotFoundException(teacherId));
        String before = describe(profile, currentSalary(teacherId).amount());

        profile.updateContact(request.name(), request.phone(), request.email());
        teacherProfileRepository.save(profile);

        auditWriter.record(new AuditRecordRequest(
                "teacher", ENTITY_TYPE, teacherId.toString(), AuditAction.UPDATED,
                "Teacher profile updated", before, describe(profile, currentSalary(teacherId).amount()), actingUserId, "ADMIN",
                com.hls.CorrelationIdFilter.currentCorrelationId()));

        return toView(profile);
    }

    @Override
    @Transactional
    public TeacherProfileView changeStatus(UUID teacherId, TeacherStatus newStatus, UUID actingUserId) {
        TeacherProfile profile = teacherProfileRepository.findById(teacherId)
                .orElseThrow(() -> new TeacherNotFoundException(teacherId));
        String before = describe(profile, currentSalary(teacherId).amount());

        profile.changeStatus(newStatus);
        teacherProfileRepository.save(profile);

        auditWriter.record(new AuditRecordRequest(
                "teacher", ENTITY_TYPE, teacherId.toString(), AuditAction.UPDATED,
                "Teacher status changed", before, describe(profile, currentSalary(teacherId).amount()), actingUserId, "ADMIN",
                com.hls.CorrelationIdFilter.currentCorrelationId()));

        return toView(profile);
    }

    // ---- Salary commands (specs/009-teacher-salary-history) ----------------------------

    @Override
    @Transactional
    public TeacherSalaryHistoryView recordSalaryChange(UUID teacherId, BigDecimal amount, LocalDate effectiveFrom, UUID actingUserId) {
        if (!teacherProfileRepository.findById(teacherId).isPresent()) {
            throw new TeacherNotFoundException(teacherId);
        }
        LocalDate effective = effectiveFrom != null ? effectiveFrom : LocalDate.now(clock);
        BigDecimal before = currentSalary(teacherId).amount();

        TeacherSalaryHistory entry = new TeacherSalaryHistory(
                UUID.randomUUID(), teacherId, amount, effective, clock.instant(), actingUserId);
        teacherSalaryHistoryRepository.save(entry);

        auditWriter.record(new AuditRecordRequest(
                "teacher", ENTITY_TYPE, teacherId.toString(), AuditAction.UPDATED,
                "Teacher salary changed", "hlsOfferedSalary=" + before,
                "hlsOfferedSalary=" + amount + ", effectiveFrom=" + effective, actingUserId, "ADMIN",
                com.hls.CorrelationIdFilter.currentCorrelationId()));

        return new TeacherSalaryHistoryView(entry.getId(), teacherId, amount, effective);
    }

    // ---- helpers -------------------------------------------------------------------------

    private TeacherProfileView toView(TeacherProfile profile) {
        return new TeacherProfileView(
                profile.getId(), profile.getName(), profile.getPhone(), profile.getEmail(),
                currentSalary(profile.getId()).amount(), profile.getStatus(), profile.getCreatedAt());
    }

    private String describe(TeacherProfile profile, BigDecimal salary) {
        return "name=" + profile.getName() + ", phone=" + profile.getPhone() + ", email=" + profile.getEmail()
                + ", hlsOfferedSalary=" + salary + ", status=" + profile.getStatus();
    }
}
