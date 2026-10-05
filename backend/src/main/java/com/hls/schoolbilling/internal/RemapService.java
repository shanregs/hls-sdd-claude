package com.hls.schoolbilling.internal;

import com.hls.identity.user.Role;
import com.hls.organization.api.ScopeQueries;
import com.hls.school.api.CallerContext;
import com.hls.school.api.ChangeRecorder;
import com.hls.school.api.ConflictException;
import com.hls.school.api.InvalidInputException;
import com.hls.school.api.NotFoundException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Maps the Teachers already at a School to the positions of its (new) contract in one step (spec 012 FR-007).
 * All or nothing. For each Teacher: an assignment that began before the contract starts is ended the day before
 * and a new one starts under the new position on the contract's start date, so the Teacher's placement has no
 * gap; an assignment that is not mapped yet and began on or after the start simply gets its position.
 */
@Service
public class RemapService {

    private static final LocalDate FOREVER = LocalDate.of(9999, 12, 31);

    public record Entry(UUID teacherId, UUID positionId) {}

    private final ContractRepository contracts;
    private final ContractPositionRepository positions;
    private final ContractAssignmentRepository assignments;
    private final ScopeQueries scopeQueries;
    private final ChangeRecorder changes;
    private final Clock clock;

    public RemapService(
            ContractRepository contracts,
            ContractPositionRepository positions,
            ContractAssignmentRepository assignments,
            ScopeQueries scopeQueries,
            ChangeRecorder changes,
            Clock clock) {
        this.contracts = contracts;
        this.positions = positions;
        this.assignments = assignments;
        this.scopeQueries = scopeQueries;
        this.changes = changes;
        this.clock = clock;
    }

    @Transactional
    public int remap(UUID actor, Set<Role> roles, UUID contractId, List<Entry> entries) {
        Contract contract = contracts.findById(contractId)
                .orElseThrow(() -> new NotFoundException("Contract not found."));
        if (!CallerContext.isOrgWide(roles)
                && !scopeQueries.scopeOf(actor, roles).allowsSchool(contract.getSchoolId())) {
            throw new NotFoundException("Contract not found.");
        }
        if (contract.getState() != ContractState.ACTIVE) {
            throw new ConflictException("Teachers are mapped to the positions of an active MoU.");
        }
        if (entries == null || entries.isEmpty()) {
            throw new InvalidInputException("Choose at least one Teacher and position.");
        }
        Set<UUID> positionIds = positions.findByContractIdOrderByNumber(contractId).stream()
                .map(ContractPosition::getId)
                .collect(Collectors.toSet());
        Set<UUID> seenTeachers = new HashSet<>();
        Set<UUID> seenPositions = new HashSet<>();
        for (Entry e : entries) {
            if (e.teacherId() == null || e.positionId() == null) {
                throw new InvalidInputException("Each entry needs a Teacher and a position.");
            }
            if (!positionIds.contains(e.positionId())) {
                throw new InvalidInputException("A position is not on this contract.");
            }
            if (!seenTeachers.add(e.teacherId())) {
                throw new InvalidInputException("A Teacher is listed twice.");
            }
            if (!seenPositions.add(e.positionId())) {
                throw new InvalidInputException("A position is chosen for two Teachers.");
            }
        }
        LocalDate effective = contract.getStartsOn();
        UUID schoolId = contract.getSchoolId();
        List<ContractAssignment> here = assignments.atSchoolOverlapping(
                schoolId, effective, contract.getEndsOn() == null ? FOREVER : contract.getEndsOn());
        // free the positions first: a Teacher moving between two positions must not trip over their own row
        for (Entry e : entries) {
            // the row in effect on the contract's start date, else the first one that starts after it
            ContractAssignment a = here.stream()
                    .filter(x -> x.getTeacherId().equals(e.teacherId()))
                    .min(java.util.Comparator.comparing(
                            (ContractAssignment x) -> !x.isInEffectOn(effective))
                            .thenComparing(ContractAssignment::getStartsOn))
                    .orElseThrow(() -> new ConflictException(
                            "A Teacher is not placed at this School from " + effective + "."));
            UUID before = a.getPositionId();
            if (positionIds.contains(a.getPositionId()) && a.getPositionId().equals(e.positionId())) {
                continue;
            }
            if (a.getStartsOn().isBefore(effective)) {
                // the new row keeps the old row's end: an exit or a scheduled move out is not undone by a re-map
                LocalDate originalEnd = a.getEndsOn();
                a.setEndsOn(effective.minusDays(1));
                assignments.saveAndFlush(a);
                ContractAssignment next =
                        new ContractAssignment(e.teacherId(), schoolId, e.positionId(), effective, actor, clock.instant());
                next.setEndsOn(originalEnd);
                assignments.save(next);
            } else if (a.getPositionId() == null || !positionIds.contains(a.getPositionId())) {
                a.setPositionId(e.positionId());
                assignments.save(a);
            } else {
                throw new ConflictException("A Teacher is already mapped to another position of this contract.");
            }
            try {
                assignments.flush();
            } catch (DataIntegrityViolationException ex) {
                throw new ConflictException("A position is already filled on those dates.");
            }
            changes.record(actor, "CONTRACT_REMAP", e.teacherId(), "position", before, e.positionId());
        }
        return entries.size();
    }
}
