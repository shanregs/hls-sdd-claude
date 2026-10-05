package com.hls.schoolbilling.internal;

import com.hls.identity.user.Role;
import com.hls.organization.api.ManagerQueries;
import com.hls.organization.api.ManagerQueries.ManagerRef;
import com.hls.organization.api.ScopeQueries;
import com.hls.organization.api.ScopeView;
import com.hls.school.api.CallerContext;
import com.hls.school.api.PageResponse;
import com.hls.school.api.SchoolDirectory;
import com.hls.school.api.SchoolDirectory.SchoolInfo;
import com.hls.schoolbilling.internal.ContractDtos.ContractListRow;
import com.hls.schoolbilling.internal.ContractDtos.ContractStatus;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
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

/**
 * The School Contracts list (spec 012 US4): every School in the caller's scope with the contract that
 * applies today (or the next one, or the last), its status, filled and vacant positions, and the Teachers
 * at the School not mapped to the contract in effect. One batch of queries per page, however many Schools.
 */
@Service
public class ContractListService {

    private final ContractRepository contracts;
    private final ContractPositionRepository positions;
    private final ContractAssignmentRepository assignments;
    private final SchoolDirectory schools;
    private final ManagerQueries managers;
    private final ScopeQueries scopeQueries;
    private final ContractService contractService;
    private final Clock clock;

    public ContractListService(
            ContractRepository contracts,
            ContractPositionRepository positions,
            ContractAssignmentRepository assignments,
            SchoolDirectory schools,
            ManagerQueries managers,
            ScopeQueries scopeQueries,
            ContractService contractService,
            Clock clock) {
        this.contracts = contracts;
        this.positions = positions;
        this.assignments = assignments;
        this.schools = schools;
        this.managers = managers;
        this.scopeQueries = scopeQueries;
        this.contractService = contractService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<ContractListRow> list(
            UUID userId, Set<Role> roles, ContractStatus status, UUID managerId, int page, int size) {
        LocalDate today = LocalDate.now(clock);
        List<SchoolInfo> inScope = schools.allSchools();
        if (!CallerContext.isOrgWide(roles)) {
            ScopeView scope = scopeQueries.scopeOf(userId, roles);
            inScope = inScope.stream().filter(s -> scope.allowsSchool(s.id())).toList();
        }
        List<UUID> schoolIds = inScope.stream().map(SchoolInfo::id).toList();
        Set<UUID> schoolIdSet = new HashSet<>(schoolIds);
        if (schoolIds.isEmpty()) {
            return new PageResponse<>(List.of(), page, size, 0);
        }
        Map<UUID, ManagerRef> managerOf = managers.managersOfSchools(schoolIds);

        Map<UUID, List<Contract>> bySchool = contracts.findAllLive().stream()
                .filter(c -> schoolIdSet.contains(c.getSchoolId()))
                .collect(Collectors.groupingBy(Contract::getSchoolId));
        Map<UUID, Contract> shown = new HashMap<>();
        bySchool.forEach((id, list) -> shown.put(id, pick(list, today)));

        Map<UUID, List<ContractPosition>> positionsByContract = positions
                .findByContractIdInOrderByContractIdAscNumberAsc(
                        shown.values().stream().map(Contract::getId).toList())
                .stream()
                .collect(Collectors.groupingBy(ContractPosition::getContractId));
        Map<UUID, List<ContractAssignment>> hereToday = assignments.atSchoolsOn(schoolIds, today).stream()
                .collect(Collectors.groupingBy(ContractAssignment::getSchoolId));

        List<ContractListRow> rows = new java.util.ArrayList<>();
        for (SchoolInfo school : inScope) {
            Contract c = shown.get(school.id());
            ManagerRef manager = managerOf.get(school.id());
            if (managerId != null && (manager == null || !managerId.equals(manager.id()))) {
                continue;
            }
            // positions of the contract in effect today decide who counts as mapped
            Contract inEffect = bySchool.getOrDefault(school.id(), List.of()).stream()
                    .filter(x -> x.covers(today))
                    .findFirst()
                    .orElse(null);
            Set<UUID> effectivePositions = new HashSet<>();
            if (inEffect != null && !inEffect.isPending()) {
                positionsOf(inEffect, positionsByContract, shown).forEach(p -> effectivePositions.add(p.getId()));
            }
            List<ContractAssignment> here = hereToday.getOrDefault(school.id(), List.of());
            int unmapped = (int) here.stream()
                    .filter(a -> a.getPositionId() == null || !effectivePositions.contains(a.getPositionId()))
                    .count();
            int filled = (int) here.stream()
                    .map(ContractAssignment::getPositionId)
                    .filter(effectivePositions::contains)
                    .distinct()
                    .count();
            ContractStatus rowStatus = c == null ? ContractStatus.NONE : contractService.statusOf(c, today);
            // a School with Teachers but no MoU recorded yet is listed as pending, even before a contract row exists
            Integer count = c == null || c.isPending() ? null : c.getTeacherCount();
            int vacant = count == null ? 0 : Math.max(0, count - filled);
            rows.add(new ContractListRow(
                    school.id(),
                    school.name(),
                    manager == null ? null : manager.displayName(),
                    rowStatus,
                    c == null ? null : c.getId(),
                    c == null ? null : c.getStartsOn(),
                    c == null ? null : c.getEndsOn(),
                    count,
                    count == null ? 0 : filled,
                    vacant,
                    unmapped,
                    c == null || c.getSalaryMode() == null ? null : c.getSalaryMode().name(),
                    c == null ? null : c.getSignedOn()));
        }
        List<ContractListRow> filtered = rows.stream()
                .filter(r -> status == null || r.status() == status)
                .sorted(Comparator.comparing(ContractListRow::schoolName, String.CASE_INSENSITIVE_ORDER))
                .toList();
        int from = Math.min(filtered.size(), Math.max(0, page) * Math.max(1, size));
        int to = Math.min(filtered.size(), from + Math.max(1, size));
        return new PageResponse<>(filtered.subList(from, to), page, size, filtered.size());
    }

    private static List<ContractPosition> positionsOf(
            Contract inEffect, Map<UUID, List<ContractPosition>> byContract, Map<UUID, Contract> shown) {
        // the contract in effect is the one shown unless the School only has a future or ended one
        return byContract.getOrDefault(inEffect.getId(), List.of());
    }

    /** The contract that applies today, else the next one to start, else the one that ended last. */
    private static Contract pick(List<Contract> list, LocalDate today) {
        Optional<Contract> now = list.stream().filter(c -> c.covers(today)).findFirst();
        if (now.isPresent()) {
            return now.get();
        }
        Optional<Contract> next = list.stream()
                .filter(c -> c.getStartsOn().isAfter(today))
                .min(Comparator.comparing(Contract::getStartsOn));
        if (next.isPresent()) {
            return next.get();
        }
        return list.stream()
                .max(Comparator.comparing(c -> c.getEndsOn() == null ? LocalDate.MAX : c.getEndsOn()))
                .orElseThrow();
    }
}
