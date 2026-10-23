package com.hls.payroll.run.internal;

import com.hls.payroll.api.PayRules.Kind;
import com.hls.payroll.api.ReimbursementIntake;
import com.hls.payroll.internal.PersonDirectory;
import com.hls.organization.api.ManagerQueries;
import com.hls.school.api.ChangeRecorder;
import com.hls.teacher.api.TeacherDirectory;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates, withdraws and reports {@code REIMBURSEMENT} adjustments for expense claims (spec 015). The adjustment rules
 * stay where they were: a draft run takes a waiting adjustment at compute time, cancelling frees it, approval applies
 * it. Nothing here touches an approved run.
 */
@Service
class ReimbursementIntakeImpl implements ReimbursementIntake {

    private static final int MAX_REASON = 500;
    private static final int MAX_SOURCE = 100;

    private record Person(Kind kind, UUID id, String name) {}

    private final PayAdjustmentRepository adjustments;
    private final PayRunRepository runs;
    private final AdjustmentService adjustmentService;
    private final PersonDirectory people;
    private final ManagerQueries managers;
    private final TeacherDirectory teachers;
    private final ChangeRecorder changes;
    private final Clock clock;

    ReimbursementIntakeImpl(
            PayAdjustmentRepository adjustments,
            PayRunRepository runs,
            AdjustmentService adjustmentService,
            PersonDirectory people,
            ManagerQueries managers,
            TeacherDirectory teachers,
            ChangeRecorder changes,
            Clock clock) {
        this.adjustments = adjustments;
        this.runs = runs;
        this.adjustmentService = adjustmentService;
        this.people = people;
        this.managers = managers;
        this.teachers = teachers;
        this.changes = changes;
        this.clock = clock;
    }

    @Override
    @Transactional
    public OfferResult offerForUser(UUID actor, UUID userId, BigDecimal amount, String reason, String sourceRef) {
        Optional<Person> person = personOf(userId);
        if (person.isEmpty()) {
            return OfferResult.refused("This person is not on the payroll, so the claim cannot be paid with salary.");
        }
        return create(actor, person.get(), amount, reason, sourceRef);
    }

    @Override
    @Transactional
    public OfferResult offer(UUID actor, Kind kind, UUID personId, BigDecimal amount, String reason, String sourceRef) {
        String name = switch (kind) {
            case MANAGER -> people.allManagers().stream()
                    .filter(m -> m.id().equals(personId))
                    .map(PersonDirectory.ManagerSummary::name)
                    .findFirst()
                    .orElse(null);
            case TEACHER -> people.allTeachers().stream()
                    .filter(t -> t.id().equals(personId))
                    .map(PersonDirectory.TeacherSummary::name)
                    .findFirst()
                    .orElse(null);
            case STAFF -> Optional.ofNullable(people.staff(Set.of(personId)).get(personId))
                    .map(PersonDirectory.StaffUser::name)
                    .orElse(null);
        };
        if (name == null) {
            return OfferResult.refused("No such person on the payroll.");
        }
        return create(actor, new Person(kind, personId, name), amount, reason, sourceRef);
    }

    private OfferResult create(UUID actor, Person person, BigDecimal amount, String reason, String sourceRef) {
        if (sourceRef == null || sourceRef.isBlank() || sourceRef.length() > MAX_SOURCE) {
            return OfferResult.refused("The source reference is required and at most " + MAX_SOURCE + " characters.");
        }
        if (amount == null || amount.signum() <= 0) {
            return OfferResult.refused("The reimbursement must be more than zero.");
        }
        List<PayAdjustment> existing =
                adjustments.findBySourceRefAndKindOrderByCreatedAtDescIdDesc(sourceRef, PayAdjustment.AdjustmentKind.REIMBURSEMENT);
        if (!existing.isEmpty() && existing.get(0).getStatus() != PayAdjustment.Status.WITHDRAWN) {
            return OfferResult.ok();
        }
        String why = reason == null || reason.isBlank() ? "Expense reimbursement" : reason.trim();
        if (why.length() > MAX_REASON) {
            why = why.substring(0, MAX_REASON);
        }
        PayAdjustment adjustment = new PayAdjustment(
                person.kind(),
                person.id(),
                person.name(),
                PayAdjustment.AdjustmentKind.REIMBURSEMENT,
                amount.setScale(2),
                why,
                actor,
                clock.instant());
        adjustment.sourcedFrom(sourceRef);
        PayAdjustment saved = adjustments.save(adjustment);
        changes.recordLifecycle(actor, AdjustmentService.ENTITY, saved.getId(), "created", "REIMBURSEMENT for " + person.name() + ": " + why);
        return OfferResult.ok();
    }

    @Override
    @Transactional
    public boolean withdraw(UUID actor, String sourceRef, String reason) {
        List<PayAdjustment> existing =
                adjustments.findBySourceRefAndKindOrderByCreatedAtDescIdDesc(sourceRef, PayAdjustment.AdjustmentKind.REIMBURSEMENT);
        if (existing.isEmpty() || existing.get(0).getStatus() == PayAdjustment.Status.WITHDRAWN) {
            return true;
        }
        PayAdjustment adjustment = existing.get(0);
        OfferStatus status = statusOf(adjustment);
        if (status == OfferStatus.APPLIED || status == OfferStatus.HELD_AWAITING_APPROVAL) {
            return false;
        }
        adjustmentService.withdraw(actor, adjustment.getId(), reason == null || reason.isBlank() ? "Claim no longer payable" : reason);
        return true;
    }

    @Override
    @Transactional(readOnly = true)
    public OfferStatus status(String sourceRef) {
        List<PayAdjustment> existing =
                adjustments.findBySourceRefAndKindOrderByCreatedAtDescIdDesc(sourceRef, PayAdjustment.AdjustmentKind.REIMBURSEMENT);
        return existing.isEmpty() ? OfferStatus.NONE : statusOf(existing.get(0));
    }

    private OfferStatus statusOf(PayAdjustment adjustment) {
        return switch (adjustment.getStatus()) {
            case APPLIED -> OfferStatus.APPLIED;
            case WITHDRAWN -> OfferStatus.WITHDRAWN;
            case WAITING -> {
                if (adjustment.getAttachedRunId() == null) {
                    yield OfferStatus.WAITING;
                }
                yield runs.findById(adjustment.getAttachedRunId())
                        .map(run -> switch (run.getStatus()) {
                            case DRAFT -> OfferStatus.HELD_BY_DRAFT;
                            case AWAITING_APPROVAL -> OfferStatus.HELD_AWAITING_APPROVAL;
                            default -> OfferStatus.WAITING;
                        })
                        .orElse(OfferStatus.WAITING);
            }
        };
    }

    private Optional<Person> personOf(UUID userId) {
        Optional<ManagerQueries.ManagerRef> manager = managers.managerByUserId(userId);
        if (manager.isPresent()) {
            return Optional.of(new Person(Kind.MANAGER, manager.get().id(), manager.get().displayName()));
        }
        Optional<TeacherDirectory.TeacherInfo> teacher = teachers.teacherOfUser(userId);
        if (teacher.isPresent()) {
            return Optional.of(new Person(Kind.TEACHER, teacher.get().id(), teacher.get().name()));
        }
        PersonDirectory.StaffUser staff = people.staff(Set.of(userId)).get(userId);
        return staff == null ? Optional.empty() : Optional.of(new Person(Kind.STAFF, userId, staff.name()));
    }
}
