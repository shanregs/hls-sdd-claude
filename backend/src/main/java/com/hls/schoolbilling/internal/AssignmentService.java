package com.hls.schoolbilling.internal;

import com.hls.school.api.ChangeRecorder;
import com.hls.school.api.ConflictException;
import com.hls.school.api.InvalidInputException;
import com.hls.school.api.NotFoundException;
import com.hls.school.api.SchoolDirectory;
import com.hls.school.api.SchoolDirectory.SchoolInfo;
import com.hls.teacher.api.TeacherDirectory;
import com.hls.teacher.api.TeacherDirectory.TeacherInfo;
import java.time.Clock;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Teacher assignments to a School and, under an MoU, to a position of its contract (spec 012). These are the
 * rules of the interim placement of spec 005, moved unchanged: dated rows, a future date schedules a move that
 * takes effect when the date arrives (no scheduler: "current" is always evaluated against today), history is
 * never overwritten, and assignments never overlap. New: the School's contract. A School without a contract
 * covering the start date gets a "MoU pending" contract so existing flows keep working; once an active MoU
 * covers the date a vacant position is used.
 */
@Service
public class AssignmentService {

    private static final LocalDate FOREVER = LocalDate.of(9999, 12, 31);

    private final TeacherDirectory teachers;
    private final SchoolDirectory schools;
    private final ContractRepository contracts;
    private final ContractPositionRepository positions;
    private final ContractAssignmentRepository assignments;
    private final ChangeRecorder changes;
    private final Clock clock;

    public AssignmentService(
            TeacherDirectory teachers,
            SchoolDirectory schools,
            ContractRepository contracts,
            ContractPositionRepository positions,
            ContractAssignmentRepository assignments,
            ChangeRecorder changes,
            Clock clock) {
        this.teachers = teachers;
        this.schools = schools;
        this.contracts = contracts;
        this.positions = positions;
        this.assignments = assignments;
        this.changes = changes;
        this.clock = clock;
    }

    /** Assigns or moves the Teacher to {@code schoolId} effective {@code effectiveOn} (default today). */
    @Transactional
    public void assign(UUID actor, UUID teacherId, UUID schoolId, UUID positionId, LocalDate effectiveOn) {
        TeacherInfo teacher = teachers.teacherInfo(List.of(teacherId)).get(teacherId);
        if (teacher == null) {
            throw new NotFoundException("Teacher not found.");
        }
        if ("EXITED".equals(teacher.status())) {
            throw new ConflictException("An exited Teacher cannot be placed in a School.");
        }
        if (schoolId == null) {
            throw new InvalidInputException("A School is required.");
        }
        SchoolInfo school =
                schools.school(schoolId).orElseThrow(() -> new InvalidInputException("School not found."));
        if (!school.active()) {
            throw new ConflictException("This School is inactive.");
        }
        LocalDate today = LocalDate.now(clock);
        LocalDate start = effectiveOn == null ? today : effectiveOn;

        List<ContractAssignment> active =
                assignments.findByTeacherIdAndStatusOrderByStartsOnDesc(teacherId, AssignmentStatus.ACTIVE);
        Optional<ContractAssignment> current =
                active.stream().filter(p -> p.isInEffectOn(today)).findFirst();
        Optional<ContractAssignment> pending =
                active.stream().filter(p -> p.getStartsOn().isAfter(today)).findFirst();
        UUID beforeSchool = current.map(ContractAssignment::getSchoolId).orElse(null);
        UUID beforePosition = current.map(ContractAssignment::getPositionId).orElse(null);

        Contract contract = contractFor(actor, schoolId, start);
        Set<UUID> ownRows = new HashSet<>();
        current.ifPresent(c -> ownRows.add(c.getId()));
        pending.ifPresent(p -> ownRows.add(p.getId()));
        UUID resolvedPosition = resolvePosition(
                contract, positionId, start, ownRows, current.orElse(null), schoolId);

        if (current.isPresent()
                && current.get().getSchoolId().equals(schoolId)
                && java.util.Objects.equals(current.get().getPositionId(), resolvedPosition)
                && pending.isEmpty()) {
            throw new ConflictException("The Teacher is already placed in this School.");
        }

        // a new instruction replaces any scheduled move; restore the current assignment first
        if (pending.isPresent()) {
            cancelPendingRow(pending.get(), current.orElse(null));
            assignments.flush();
        }
        if (start.isAfter(today)) {
            schedule(actor, teacherId, schoolId, resolvedPosition, start, current.orElse(null));
        } else {
            moveNow(actor, teacherId, schoolId, resolvedPosition, start, current.orElse(null), active);
        }
        flush();
        changes.record(actor, "TEACHER_PLACEMENT", teacherId, "school", beforeSchool, schoolId);
        changes.record(actor, "TEACHER_PLACEMENT", teacherId, "effectiveOn", null, start);
        if (resolvedPosition != null || beforePosition != null) {
            changes.record(actor, "TEACHER_PLACEMENT", teacherId, "position", beforePosition, resolvedPosition);
        }
    }

    /** Cancels the scheduled future move and restores the current assignment's open end. */
    @Transactional
    public void cancelPending(UUID actor, UUID teacherId) {
        if (!teachers.teacherInfo(List.of(teacherId)).containsKey(teacherId)) {
            throw new NotFoundException("Teacher not found.");
        }
        LocalDate today = LocalDate.now(clock);
        List<ContractAssignment> active =
                assignments.findByTeacherIdAndStatusOrderByStartsOnDesc(teacherId, AssignmentStatus.ACTIVE);
        ContractAssignment pending = active.stream()
                .filter(p -> p.getStartsOn().isAfter(today))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("There is no scheduled move to cancel."));
        ContractAssignment current =
                active.stream().filter(p -> p.isInEffectOn(today)).findFirst().orElse(null);
        UUID scheduledSchool = pending.getSchoolId();
        cancelPendingRow(pending, current);
        flush();
        changes.record(actor, "TEACHER_PLACEMENT", teacherId, "scheduledSchool", scheduledSchool, null);
    }

    /** Ends the current assignment on the exit date and cancels a scheduled one (Teacher exit). */
    @Transactional
    public void endForExit(UUID teacherId, LocalDate exitDate) {
        LocalDate today = LocalDate.now(clock);
        List<ContractAssignment> active =
                assignments.findByTeacherIdAndStatusOrderByStartsOnDesc(teacherId, AssignmentStatus.ACTIVE);
        ContractAssignment current =
                active.stream().filter(p -> p.isInEffectOn(today)).findFirst().orElse(null);
        for (ContractAssignment row : active) {
            if (row.getStartsOn().isAfter(today) || row.getStartsOn().isAfter(exitDate)) {
                row.setStatus(AssignmentStatus.CANCELLED);
                row.setEndsOn(null);
                assignments.save(row);
            }
        }
        if (current != null && current.getStatus() == AssignmentStatus.ACTIVE) {
            if (exitDate.isAfter(current.getStartsOn())) {
                // the Teacher is no longer placed from the exit date itself, so the position is free from then
                current.setEndsOn(exitDate.minusDays(1));
            } else {
                // the exit predates or equals the assignment start: it never took effect
                current.setStatus(AssignmentStatus.CANCELLED);
                current.setEndsOn(null);
            }
            assignments.save(current);
        }
        flush();
    }

    /**
     * The School's live contract covering {@code start}. When none does, creates a "MoU pending" contract from
     * that date (open-ended, or up to the day before the next contract) so the assignment has a home.
     */
    private Contract contractFor(UUID actor, UUID schoolId, LocalDate start) {
        Optional<Contract> covering = contracts.liveOn(schoolId, start);
        if (covering.isPresent()) {
            return covering.get();
        }
        LocalDate nextStart = contracts.findBySchoolIdOrderByStartsOnDesc(schoolId).stream()
                .filter(c -> c.getState() != ContractState.CANCELLED && c.getStartsOn().isAfter(start))
                .map(Contract::getStartsOn)
                .min(LocalDate::compareTo)
                .orElse(null);
        Contract pending = Contract.pending(schoolId, start, actor, clock.instant());
        if (nextStart != null) {
            pending.setEndsOn(nextStart.minusDays(1));
        }
        Contract saved;
        try {
            saved = contracts.saveAndFlush(pending);
        } catch (DataIntegrityViolationException e) {
            // two first assignments to the same School at once: the other one created its contract
            throw new ConflictException("This School's contract was just changed by someone else. Try again.");
        }
        changes.record(actor, "CONTRACT", saved.getId(), "state", null, ContractState.RATE_PENDING.name());
        return saved;
    }

    /**
     * The position to use: none under a pending contract; under an active MoU the named one, or for a
     * same-for-all contract the next vacant one. A position must belong to the contract and be vacant.
     */
    private UUID resolvePosition(
            Contract contract,
            UUID positionId,
            LocalDate start,
            Set<UUID> ownRows,
            ContractAssignment current,
            UUID schoolId) {
        if (contract.isPending()) {
            if (positionId != null) {
                throw new InvalidInputException("This School has no MoU yet, so there are no positions to choose.");
            }
            return null;
        }
        List<ContractPosition> all = positions.findByContractIdOrderByNumber(contract.getId());
        List<UUID> positionIds = all.stream().map(ContractPosition::getId).toList();
        List<ContractAssignment> filled = assignments.onPositionsOverlapping(positionIds, start, FOREVER);
        // the Teacher's own current and scheduled rows do not make a position unavailable to the Teacher
        Set<UUID> taken = new HashSet<>();
        for (ContractAssignment a : filled) {
            if (!ownRows.contains(a.getId())) {
                taken.add(a.getPositionId());
            }
        }
        if (positionId != null) {
            if (!positionIds.contains(positionId)) {
                throw new InvalidInputException("That position is not on this School's contract.");
            }
            if (taken.contains(positionId)) {
                throw new ConflictException("That position is already filled on those dates.");
            }
            return positionId;
        }
        // no position named: a Teacher already on a position of this contract at this School keeps it
        if (current != null
                && current.getSchoolId().equals(schoolId)
                && current.getPositionId() != null
                && positionIds.contains(current.getPositionId())) {
            return current.getPositionId();
        }
        if (contract.getSalaryMode() == SalaryMode.PER_TEACHER) {
            throw new InvalidInputException("Choose the position this Teacher fills.");
        }
        return all.stream()
                .filter(p -> !taken.contains(p.getId()))
                .map(ContractPosition::getId)
                .findFirst()
                .orElseThrow(() -> new ConflictException("All " + all.size()
                        + " positions are filled; record a new contract to add Teachers."));
    }

    private void schedule(
            UUID actor,
            UUID teacherId,
            UUID schoolId,
            UUID positionId,
            LocalDate start,
            ContractAssignment current) {
        if (current != null) {
            current.setEndsOn(start.minusDays(1));
            assignments.saveAndFlush(current);
        }
        assignments.save(new ContractAssignment(teacherId, schoolId, positionId, start, actor, clock.instant()));
    }

    private void moveNow(
            UUID actor,
            UUID teacherId,
            UUID schoolId,
            UUID positionId,
            LocalDate start,
            ContractAssignment current,
            List<ContractAssignment> allActive) {
        if (current != null) {
            if (start.isBefore(current.getStartsOn())) {
                throw new ConflictException(
                        "The date cannot be earlier than the start of the current placement ("
                                + current.getStartsOn() + ").");
            }
            if (start.equals(current.getStartsOn())) {
                current.setStatus(AssignmentStatus.CORRECTED);
            } else {
                current.setEndsOn(start.minusDays(1));
            }
            assignments.saveAndFlush(current);
        } else {
            LocalDate lastEnd = allActive.stream()
                    .filter(p -> p.getEndsOn() != null)
                    .map(ContractAssignment::getEndsOn)
                    .max(LocalDate::compareTo)
                    .orElse(null);
            if (lastEnd != null && !start.isAfter(lastEnd)) {
                throw new ConflictException("The date overlaps an earlier placement that ended on " + lastEnd + ".");
            }
        }
        assignments.save(new ContractAssignment(teacherId, schoolId, positionId, start, actor, clock.instant()));
    }

    private void cancelPendingRow(ContractAssignment pending, ContractAssignment current) {
        pending.setStatus(AssignmentStatus.CANCELLED);
        assignments.save(pending);
        if (current != null
                && current.getEndsOn() != null
                && current.getEndsOn().equals(pending.getStartsOn().minusDays(1))) {
            current.setEndsOn(null);
            assignments.save(current);
        }
    }

    private void flush() {
        try {
            assignments.flush();
        } catch (DataIntegrityViolationException e) {
            String cause = String.valueOf(e.getMostSpecificCause().getMessage());
            if (cause.contains("ex_assignment_position_dates")) {
                throw new ConflictException("That position is already filled on those dates.");
            }
            throw new ConflictException("This placement overlaps another placement of the same Teacher.");
        } catch (CannotAcquireLockException e) {
            // two requests inserting into the same exclusion range can deadlock; one is rolled back, the user retries
            throw new ConflictException("Someone else is changing this School's assignments. Try again.");
        }
    }
}
