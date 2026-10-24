package com.hls.schoolbilling.internal;

import com.hls.school.api.ConflictException;
import com.hls.school.api.InvalidInputException;
import com.hls.school.api.SchoolDirectory;
import com.hls.school.api.SchoolDirectory.SchoolInfo;
import com.hls.schoolbilling.api.PlacementMoves;
import com.hls.teacher.api.TeacherDirectory;
import com.hls.teacher.api.TeacherDirectory.TeacherInfo;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
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
 * Transfer and replacement moves for spec 027. Reuses the contract lookup and position rules of
 * {@link AssignmentService} (no copy of the rules) and the existing exclusion constraints, so no day has two Schools for a
 * Teacher or two Teachers on a position. {@link #check} and {@link #apply} share {@link #validate}.
 */
@Service
class PlacementMovesImpl implements PlacementMoves {

    private static final LocalDate FOREVER = LocalDate.of(9999, 12, 31);

    private final AssignmentService service;
    private final TeacherDirectory teachers;
    private final SchoolDirectory schools;
    private final ContractRepository contracts;
    private final ContractPositionRepository positions;
    private final ContractAssignmentRepository assignments;
    private final Clock clock;

    PlacementMovesImpl(
            AssignmentService service,
            TeacherDirectory teachers,
            SchoolDirectory schools,
            ContractRepository contracts,
            ContractPositionRepository positions,
            ContractAssignmentRepository assignments,
            Clock clock) {
        this.service = service;
        this.teachers = teachers;
        this.schools = schools;
        this.contracts = contracts;
        this.positions = positions;
        this.assignments = assignments;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public MoveCheck check(MoveSpec spec) {
        List<String> problems = validate(spec);
        return new MoveCheck(problems.isEmpty(), List.copyOf(problems));
    }

    @Override
    @Transactional
    public AppliedMove apply(UUID actorUserId, MoveSpec spec) {
        List<String> problems = validate(spec);
        if (!problems.isEmpty()) {
            throw new ConflictException(String.join(" ", problems));
        }
        LocalDate on = spec.effectiveOn();
        ContractAssignment outgoing = currentRow(spec.outgoingTeacherId(), spec.schoolId(), on).orElseThrow();
        UUID outgoingPosition = outgoing.getPositionId();
        List<UUID> ended = new ArrayList<>();
        List<UUID> created = new ArrayList<>();
        try {
            outgoing.setEndsOn(on.minusDays(1));
            assignments.saveAndFlush(outgoing);
            ended.add(outgoing.getId());
            if (spec.kind() == Kind.TRANSFER) {
                created.add(newRow(actorUserId, spec.outgoingTeacherId(), spec.toSchoolId(), spec.positionId(), on));
            } else {
                // the replacement reuses the position; a School with no positions places them with none
                ContractAssignment incoming = new ContractAssignment(
                        spec.incomingTeacherId(), spec.schoolId(), outgoingPosition, on, actorUserId, clock.instant());
                created.add(assignments.saveAndFlush(incoming).getId());
                if (spec.outgoingDestinationSchoolId() != null) {
                    created.add(newRow(actorUserId, spec.outgoingTeacherId(), spec.outgoingDestinationSchoolId(), null, on));
                }
            }
            assignments.flush();
        } catch (DataIntegrityViolationException e) {
            String cause = String.valueOf(e.getMostSpecificCause().getMessage());
            if (cause.contains("ex_assignment_position_dates")) {
                throw new ConflictException("That position is already filled on those dates.");
            }
            throw new ConflictException("This move overlaps another placement of the same Teacher.");
        } catch (CannotAcquireLockException e) {
            throw new ConflictException("Someone else is changing this School's assignments. Try again.");
        } catch (InvalidInputException e) {
            throw new ConflictException(e.getMessage());
        }
        return new AppliedMove(List.copyOf(ended), List.copyOf(created));
    }

    @Override
    @Transactional
    public void reverse(UUID actorUserId, AppliedMove move) {
        LocalDate today = LocalDate.now(clock);
        List<ContractAssignment> createdRows = new ArrayList<>();
        for (UUID id : move.createdAssignmentIds()) {
            ContractAssignment row = assignments.findById(id).orElse(null);
            if (row == null || row.getStatus() != AssignmentStatus.ACTIVE) {
                continue; // already cancelled by another route
            }
            if (!row.getStartsOn().isAfter(today)) {
                throw new ConflictException("The move has already started and cannot be undone.");
            }
            createdRows.add(row);
        }
        for (ContractAssignment row : createdRows) {
            row.setStatus(AssignmentStatus.CANCELLED);
            assignments.save(row);
        }
        assignments.flush();
        for (UUID id : move.endedAssignmentIds()) {
            assignments.findById(id).ifPresent(row -> {
                if (row.getStatus() == AssignmentStatus.ACTIVE) {
                    row.setEndsOn(null);
                    assignments.save(row);
                }
            });
        }
        try {
            assignments.flush();
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("The earlier placement cannot be restored because it now overlaps another one.");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<VacantPosition> vacantPositions(UUID schoolId, LocalDate on) {
        Optional<Contract> contract = contracts.liveOn(schoolId, on);
        if (contract.isEmpty() || contract.get().isPending()) {
            return List.of();
        }
        List<ContractPosition> all = positions.findByContractIdOrderByNumber(contract.get().getId());
        List<UUID> ids = all.stream().map(ContractPosition::getId).toList();
        if (ids.isEmpty()) {
            return List.of();
        }
        Set<UUID> taken = new HashSet<>();
        for (ContractAssignment a : assignments.onPositionsOverlapping(ids, on, FOREVER)) {
            taken.add(a.getPositionId());
        }
        return all.stream()
                .filter(p -> !taken.contains(p.getId()))
                .map(p -> new VacantPosition(p.getId(), p.getNumber(), p.getSalary()))
                .toList();
    }

    // ---- rules shared by check and apply ----

    private List<String> validate(MoveSpec spec) {
        List<String> problems = new ArrayList<>();
        LocalDate on = spec.effectiveOn();
        if (on == null) {
            problems.add("An effective date is required.");
            return problems;
        }
        TeacherInfo outgoing = teachers.teacherInfo(List.of(spec.outgoingTeacherId())).get(spec.outgoingTeacherId());
        if (outgoing == null) {
            problems.add("Teacher not found.");
            return problems;
        }
        String teacherProblem = statusProblem(outgoing, "This Teacher", true);
        if (teacherProblem != null) {
            problems.add(teacherProblem);
            return problems;
        }
        Optional<ContractAssignment> current = currentRow(spec.outgoingTeacherId(), spec.schoolId(), on);
        if (current.isEmpty()) {
            problems.add("The Teacher is not placed at this School on " + on.format(DMY) + ".");
            return problems;
        }
        ContractAssignment row = current.get();
        if (!on.isAfter(row.getStartsOn())) {
            problems.add("The effective date must be after the day the current placement started ("
                    + row.getStartsOn().format(DMY) + ").");
        }
        if (row.getEndsOn() != null) {
            problems.add("The Teacher's placement at this School already ends on " + row.getEndsOn().format(DMY) + ".");
        }
        boolean futureRow = assignments.findByTeacherIdAndStatusOrderByStartsOnDesc(spec.outgoingTeacherId(), AssignmentStatus.ACTIVE)
                .stream()
                .anyMatch(a -> !a.getId().equals(row.getId()) && (a.getEndsOn() == null || !a.getEndsOn().isBefore(on)));
        if (futureRow) {
            problems.add("A move is already scheduled for this Teacher.");
        }
        if (spec.kind() == Kind.TRANSFER) {
            validateReceiving(problems, spec.toSchoolId(), spec.positionId(), on, spec.schoolId(), true);
        } else {
            validateReplacement(problems, spec, on);
        }
        return problems;
    }

    private void validateReplacement(List<String> problems, MoveSpec spec, LocalDate on) {
        UUID incomingId = spec.incomingTeacherId();
        if (incomingId == null) {
            problems.add("Choose the Teacher who replaces them.");
            return;
        }
        if (incomingId.equals(spec.outgoingTeacherId())) {
            problems.add("A Teacher cannot replace themselves.");
            return;
        }
        TeacherInfo incoming = teachers.teacherInfo(List.of(incomingId)).get(incomingId);
        if (incoming == null) {
            problems.add("The replacement Teacher was not found.");
            return;
        }
        String incomingProblem = statusProblem(incoming, "The replacement Teacher", false);
        if (incomingProblem != null) {
            problems.add(incomingProblem);
            return;
        }
        boolean placed = assignments.findByTeacherIdAndStatusOrderByStartsOnDesc(incomingId, AssignmentStatus.ACTIVE).stream()
                .anyMatch(a -> a.getEndsOn() == null || !a.getEndsOn().isBefore(on));
        if (placed) {
            problems.add("The replacement Teacher is already placed (or has a move scheduled) from " + on.format(DMY) + ".");
        }
        if (spec.outgoingDestinationSchoolId() != null) {
            if (spec.outgoingDestinationSchoolId().equals(spec.schoolId())) {
                problems.add("The destination must be a different School.");
            } else {
                validateReceiving(problems, spec.outgoingDestinationSchoolId(), null, on, spec.schoolId(), false);
            }
        }
    }

    private void validateReceiving(List<String> problems, UUID toSchoolId, UUID positionId, LocalDate on, UUID fromSchoolId, boolean transfer) {
        if (toSchoolId == null) {
            problems.add("Choose the receiving School.");
            return;
        }
        if (toSchoolId.equals(fromSchoolId)) {
            problems.add("The receiving School must be different from the current School.");
            return;
        }
        Optional<SchoolInfo> school = schools.school(toSchoolId);
        if (school.isEmpty()) {
            problems.add("The receiving School was not found.");
            return;
        }
        if (!school.get().active()) {
            problems.add("The receiving School is inactive.");
            return;
        }
        Optional<Contract> contract = contracts.liveOn(toSchoolId, on);
        if (contract.isEmpty() || contract.get().isPending()) {
            if (positionId != null) {
                problems.add("This School has no MoU yet, so there are no positions to choose.");
            }
            return;
        }
        try {
            service.resolvePosition(contract.get(), positionId, on, Set.of(), null, toSchoolId);
        } catch (InvalidInputException | ConflictException e) {
            problems.add(e.getMessage());
        }
    }

    private static final java.time.format.DateTimeFormatter DMY = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private static String statusProblem(TeacherInfo teacher, String who, boolean allowOnLeave) {
        return switch (teacher.status()) {
            case "ACTIVE" -> null;
            case "ON_LEAVE" -> allowOnLeave ? null : who + " is on leave.";
            case "IN_TRAINING" -> who + " is still in training.";
            case "EXITED" -> who + " has exited.";
            default -> who + " cannot be moved in their current status.";
        };
    }

    private Optional<ContractAssignment> currentRow(UUID teacherId, UUID schoolId, LocalDate on) {
        return assignments.findByTeacherIdAndStatusOrderByStartsOnDesc(teacherId, AssignmentStatus.ACTIVE).stream()
                .filter(a -> a.isInEffectOn(on) && a.getSchoolId().equals(schoolId))
                .findFirst();
    }

    private UUID newRow(UUID actor, UUID teacherId, UUID schoolId, UUID positionId, LocalDate start) {
        Contract contract = service.contractFor(actor, schoolId, start);
        UUID resolved = service.resolvePosition(contract, positionId, start, Set.of(), null, schoolId);
        ContractAssignment row = new ContractAssignment(teacherId, schoolId, resolved, start, actor, clock.instant());
        return assignments.saveAndFlush(row).getId();
    }
}
