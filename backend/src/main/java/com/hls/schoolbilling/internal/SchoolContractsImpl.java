package com.hls.schoolbilling.internal;

import com.hls.schoolbilling.api.ContractView;
import com.hls.schoolbilling.api.PositionView;
import com.hls.schoolbilling.api.SchoolContracts;
import com.hls.schoolbilling.api.TeacherPosition;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The public read interface of the contracts (spec 012 FR-017). */
@Service
@Transactional(readOnly = true)
class SchoolContractsImpl implements SchoolContracts {

    private final ContractRepository contracts;
    private final ContractPositionRepository positions;
    private final ContractAssignmentRepository assignments;

    SchoolContractsImpl(
            ContractRepository contracts,
            ContractPositionRepository positions,
            ContractAssignmentRepository assignments) {
        this.contracts = contracts;
        this.positions = positions;
        this.assignments = assignments;
    }

    @Override
    public Optional<ContractView> contractOf(UUID schoolId, LocalDate date) {
        return contracts.liveOn(schoolId, date).map(c -> views(List.of(c)).get(0));
    }

    @Override
    public Optional<TeacherPosition> positionOf(UUID teacherId, LocalDate date) {
        for (ContractAssignment a : assignments.inEffectOn(List.of(teacherId), date)) {
            if (a.getPositionId() == null) {
                continue;
            }
            Optional<ContractPosition> position = positions.findById(a.getPositionId());
            if (position.isEmpty()) {
                continue;
            }
            Optional<Contract> contract = contracts.findById(position.get().getContractId());
            if (contract.isPresent() && contract.get().covers(date)) {
                ContractPosition p = position.get();
                return Optional.of(new TeacherPosition(
                        contract.get().getId(), p.getId(), p.getNumber(), a.getSchoolId(), p.getSalary()));
            }
        }
        return Optional.empty();
    }

    @Override
    public List<ContractView> contractsOverlapping(UUID schoolId, LocalDate from, LocalDate to) {
        return views(contracts.liveOverlapping(List.of(schoolId), from, to));
    }

    @Override
    public Set<UUID> unmappedTeachers(UUID schoolId, LocalDate from, LocalDate to) {
        Set<UUID> result = new HashSet<>();
        Map<UUID, Boolean> mappedPosition = new HashMap<>();
        for (ContractAssignment a : assignments.atSchoolOverlapping(schoolId, from, to)) {
            LocalDate on = a.getStartsOn().isAfter(from) ? a.getStartsOn() : from;
            boolean mapped = false;
            if (a.getPositionId() != null) {
                mapped = mappedPosition.computeIfAbsent(a.getPositionId(), id -> positions
                        .findById(id)
                        .flatMap(p -> contracts.findById(p.getContractId()))
                        .map(c -> c.covers(on))
                        .orElse(false));
            }
            if (!mapped) {
                result.add(a.getTeacherId());
            }
        }
        return result;
    }

    private List<ContractView> views(List<Contract> list) {
        if (list.isEmpty()) {
            return List.of();
        }
        Map<UUID, List<PositionView>> byContract = positions
                .findByContractIdInOrderByContractIdAscNumberAsc(
                        list.stream().map(Contract::getId).toList())
                .stream()
                .collect(Collectors.groupingBy(
                        ContractPosition::getContractId,
                        Collectors.mapping(
                                p -> new PositionView(p.getId(), p.getNumber(), p.getTitle(), p.getSalary()),
                                Collectors.toList())));
        return list.stream()
                .map(c -> new ContractView(
                        c.getId(),
                        c.getSchoolId(),
                        c.getState().name(),
                        c.getSalaryMode() == null ? null : c.getSalaryMode().name(),
                        c.getTeacherCount(),
                        c.getRate(),
                        c.getSignedOn(),
                        c.getStartsOn(),
                        c.getEndsOn(),
                        byContract.getOrDefault(c.getId(), List.of())))
                .toList();
    }
}
