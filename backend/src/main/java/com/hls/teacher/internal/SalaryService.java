package com.hls.teacher.internal;

import com.hls.identity.user.Role;
import com.hls.school.api.ChangeRecorder;
import com.hls.school.api.InvalidInputException;
import com.hls.school.api.NotFoundException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Append-only salary history (spec 005 US9): "current" and "as of a date" are queries over the
 * dated rows; a correction is a new row. Only reachable through the TEACHER_SALARY endpoints.
 */
@Service
public class SalaryService {

    public record Entry(UUID id, BigDecimal amount, LocalDate effectiveOn, UUID recordedBy) {}

    public record History(Entry current, List<Entry> history) {}

    public record AsOf(BigDecimal amount, LocalDate effectiveOn) {}

    private static final BigDecimal MAX_AMOUNT = new BigDecimal("9999999999.99");

    private final SalaryHistoryRepository repository;
    private final TeacherRepository teacherRepository;
    private final TeacherScopeService scopeService;
    private final ChangeRecorder changes;
    private final Clock clock;

    public SalaryService(
            SalaryHistoryRepository repository,
            TeacherRepository teacherRepository,
            TeacherScopeService scopeService,
            ChangeRecorder changes,
            Clock clock) {
        this.repository = repository;
        this.teacherRepository = teacherRepository;
        this.scopeService = scopeService;
        this.changes = changes;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public History history(UUID userId, Set<Role> roles, UUID teacherId) {
        requireVisible(userId, roles, teacherId);
        List<Entry> entries = repository.findByTeacherIdOrderByEffectiveOnDescCreatedAtDesc(teacherId).stream()
                .map(SalaryService::toEntry)
                .toList();
        Entry current = repository
                .findFirstByTeacherIdAndEffectiveOnLessThanEqualOrderByEffectiveOnDescCreatedAtDesc(
                        teacherId, LocalDate.now(clock))
                .map(SalaryService::toEntry)
                .orElse(null);
        return new History(current, entries);
    }

    @Transactional(readOnly = true)
    public AsOf asOf(UUID userId, Set<Role> roles, UUID teacherId, LocalDate date) {
        requireVisible(userId, roles, teacherId);
        return repository
                .findFirstByTeacherIdAndEffectiveOnLessThanEqualOrderByEffectiveOnDescCreatedAtDesc(teacherId, date)
                .map(e -> new AsOf(e.getAmount(), e.getEffectiveOn()))
                .orElse(new AsOf(null, null));
    }

    @Transactional
    public Entry record(UUID actor, Set<Role> roles, UUID teacherId, BigDecimal amount, LocalDate effectiveOn) {
        requireVisible(actor, roles, teacherId);
        if (amount == null || amount.signum() < 0) {
            throw new InvalidInputException("The salary amount must be zero or more.");
        }
        BigDecimal rounded = amount.setScale(2, RoundingMode.HALF_UP);
        if (rounded.compareTo(MAX_AMOUNT) > 0) {
            throw new InvalidInputException("The salary amount is too large.");
        }
        if (effectiveOn == null) {
            throw new InvalidInputException("The effective date is required.");
        }
        BigDecimal before = repository
                .findFirstByTeacherIdAndEffectiveOnLessThanEqualOrderByEffectiveOnDescCreatedAtDesc(
                        teacherId, LocalDate.now(clock))
                .map(SalaryHistoryEntry::getAmount)
                .orElse(null);
        SalaryHistoryEntry saved =
                repository.saveAndFlush(new SalaryHistoryEntry(teacherId, rounded, effectiveOn, actor, clock.instant()));
        changes.record(actor, "TEACHER_SALARY", teacherId, "salary", before, rounded);
        return toEntry(saved);
    }

    private void requireVisible(UUID userId, Set<Role> roles, UUID teacherId) {
        teacherRepository.findById(teacherId).orElseThrow(() -> new NotFoundException("Teacher not found."));
        if (!scopeService.teacherScope(userId, roles).allows(teacherId)) {
            throw new NotFoundException("Teacher not found.");
        }
    }

    private static Entry toEntry(SalaryHistoryEntry e) {
        return new Entry(e.getId(), e.getAmount(), e.getEffectiveOn(), e.getRecordedBy());
    }
}
