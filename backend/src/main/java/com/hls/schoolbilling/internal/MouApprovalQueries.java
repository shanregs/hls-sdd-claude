package com.hls.schoolbilling.internal;

import com.hls.identity.user.AppUser;
import com.hls.identity.user.AppUserRepository;
import com.hls.identity.user.Role;
import com.hls.organization.api.ManagerQueries;
import com.hls.organization.api.ManagerQueries.ManagerRef;
import com.hls.organization.api.ScopeQueries;
import com.hls.organization.api.ScopeView;
import com.hls.school.api.CallerContext;
import com.hls.school.api.PageResponse;
import com.hls.school.api.SchoolDirectory;
import com.hls.school.api.SchoolDirectory.SchoolInfo;
import com.hls.schoolbilling.internal.MouDtos.ApprovalDto;
import com.hls.schoolbilling.internal.MouDtos.ApprovalSummaryDto;
import com.hls.schoolbilling.internal.MouDtos.DocumentDto;
import com.hls.schoolbilling.internal.MouDtos.DocumentSummary;
import com.hls.schoolbilling.internal.MouDtos.HistoryDto;
import com.hls.schoolbilling.internal.MouDtos.PendingRow;
import com.hls.schoolbilling.internal.MouDtos.PersonRef;
import com.hls.schoolbilling.internal.MouDtos.RemovedInfo;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The read side of MoU approval (spec 031): the approval object of one contract, the summaries carried on the School page
 * and the list, and the pending list for approvers. State is derived at read time from the rows; nothing is scheduled.
 */
@Service
public class MouApprovalQueries {

    /** What the School Contracts list shows for one contract. */
    record ListInfo(MouShownState state, String documentState, Instant pendingSince) {}

    private final ContractRepository contracts;
    private final MouApprovalRepository approvals;
    private final MouDocumentRepository documents;
    private final MouApprovalEventRepository events;
    private final AppUserRepository users;
    private final SchoolDirectory schools;
    private final ManagerQueries managers;
    private final ScopeQueries scopeQueries;
    private final MouAccess access;

    MouApprovalQueries(
            ContractRepository contracts,
            MouApprovalRepository approvals,
            MouDocumentRepository documents,
            MouApprovalEventRepository events,
            AppUserRepository users,
            SchoolDirectory schools,
            ManagerQueries managers,
            ScopeQueries scopeQueries,
            MouAccess access) {
        this.contracts = contracts;
        this.approvals = approvals;
        this.documents = documents;
        this.events = events;
        this.users = users;
        this.schools = schools;
        this.managers = managers;
        this.scopeQueries = scopeQueries;
        this.access = access;
    }

    /** The full approval object of a contract the caller may see; not found when out of scope. */
    @Transactional(readOnly = true)
    public ApprovalDto detail(UUID userId, Set<Role> roles, UUID contractId) {
        return build(access.contractInScope(userId, roles, contractId), roles);
    }

    ApprovalDto build(Contract contract, Set<Role> roles) {
        MouApproval approval = approvals.findByContractId(contract.getId()).orElse(null);
        List<MouDocument> docs = documents.findByContractIdOrderByVersionNoDesc(contract.getId());
        List<MouApprovalEvent> history = events.findByContractIdOrderBySeqAsc(contract.getId());
        Set<UUID> userIds = new HashSet<>();
        collectUsers(userIds, approval);
        docs.forEach(d -> {
            userIds.add(d.getAddedBy());
            if (d.getRemovedBy() != null) {
                userIds.add(d.getRemovedBy());
            }
        });
        history.forEach(e -> {
            if (e.getActorUserId() != null) {
                userIds.add(e.getActorUserId());
            }
        });
        Map<UUID, String> names = namesOf(userIds);

        MouDocument current = docs.stream().filter(d -> !d.isRemoved()).findFirst().orElse(null);
        List<DocumentDto> documentDtos = docs.stream()
                .map(d -> documentDto(d, d == current, names))
                .toList();
        List<HistoryDto> historyDtos = history.stream()
                .map(e -> new HistoryDto(
                        e.getAt(),
                        e.getAction(),
                        e.getRound(),
                        person(e.getActorUserId(), names),
                        e.getActorRole(),
                        e.getNote(),
                        e.getDetail(),
                        e.getDocumentVersion()))
                .toList();
        ApprovalSummaryDto s = summary(contract, approval, current, docs.stream().anyMatch(d -> !d.isRemoved()), roles, names);
        return new ApprovalDto(
                s.contractId(),
                s.state(),
                s.round(),
                s.submittedBy(),
                s.submittedAt(),
                s.decidedBy(),
                s.decidedAt(),
                s.decisionReason(),
                s.selfDecided(),
                s.document(),
                documentDtos,
                historyDtos,
                s.version(),
                s.actions());
    }

    /** The approval summary of each contract, for the School page, in a fixed number of queries. */
    @Transactional(readOnly = true)
    Map<UUID, ApprovalSummaryDto> summaries(Collection<Contract> list, Set<Role> roles) {
        if (list.isEmpty()) {
            return Map.of();
        }
        List<UUID> ids = list.stream().map(Contract::getId).toList();
        Map<UUID, MouApproval> approvalByContract = new HashMap<>();
        approvals.findByContractIdIn(ids).forEach(a -> approvalByContract.put(a.getContractId(), a));
        Map<UUID, MouDocument> currentByContract = currentDocuments(ids);
        Set<UUID> userIds = new HashSet<>();
        approvalByContract.values().forEach(a -> collectUsers(userIds, a));
        currentByContract.values().forEach(d -> userIds.add(d.getAddedBy()));
        Map<UUID, String> names = namesOf(userIds);
        Map<UUID, ApprovalSummaryDto> result = new LinkedHashMap<>();
        for (Contract c : list) {
            MouDocument current = currentByContract.get(c.getId());
            result.put(
                    c.getId(),
                    summary(c, approvalByContract.get(c.getId()), current, current != null, roles, names));
        }
        return result;
    }

    /** State, document state and pending-since of each contract, for the list rows (two batch lookups). */
    Map<UUID, ListInfo> listInfo(Collection<UUID> contractIds, Map<UUID, ContractState> states) {
        if (contractIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, MouApproval> approvalByContract = new HashMap<>();
        approvals.findByContractIdIn(contractIds).forEach(a -> approvalByContract.put(a.getContractId(), a));
        Set<UUID> withDocument = new HashSet<>(documents.contractsWithDocument(contractIds));
        Map<UUID, ListInfo> result = new HashMap<>();
        for (UUID id : contractIds) {
            MouApproval a = approvalByContract.get(id);
            boolean hasDoc = withDocument.contains(id);
            MouShownState state = MouShownState.derive(states.get(id), a == null ? null : a.getStatus(), hasDoc);
            Instant pendingSince = a != null && a.getStatus() == MouApprovalStatus.PENDING ? a.getSubmittedAt() : null;
            result.put(id, new ListInfo(state, hasDoc ? "ATTACHED" : "NONE", pendingSince));
        }
        return result;
    }

    /** PENDING MoUs in the caller's scope, oldest submission first (needs Approve; the controller checks the grant). */
    @Transactional(readOnly = true)
    public PageResponse<PendingRow> pending(UUID userId, Set<Role> roles, int page, int size) {
        List<MouApproval> pending = approvals.findByStatusOrderBySubmittedAtAsc(MouApprovalStatus.PENDING);
        Map<UUID, Contract> contractById = contracts.findAllById(pending.stream().map(MouApproval::getContractId).toList())
                .stream()
                .collect(Collectors.toMap(Contract::getId, c -> c));
        ScopeView scope = CallerContext.isOrgWide(roles) ? null : scopeQueries.scopeOf(userId, roles);
        List<MouApproval> visible = pending.stream()
                .filter(a -> {
                    Contract c = contractById.get(a.getContractId());
                    return c != null
                            && c.getState() != ContractState.CANCELLED
                            && (scope == null || scope.allowsSchool(c.getSchoolId()));
                })
                .toList();
        int from = Math.min(visible.size(), Math.max(0, page) * Math.max(1, size));
        int to = Math.min(visible.size(), from + Math.max(1, size));
        List<MouApproval> slice = visible.subList(from, to);
        List<UUID> schoolIds = slice.stream().map(a -> contractById.get(a.getContractId()).getSchoolId()).distinct().toList();
        Map<UUID, SchoolInfo> schoolInfo = schoolIds.stream()
                .map(schools::school)
                .flatMap(java.util.Optional::stream)
                .collect(Collectors.toMap(SchoolInfo::id, s -> s, (a, b) -> a));
        Map<UUID, ManagerRef> managerOf = schoolIds.isEmpty() ? Map.of() : managers.managersOfSchools(schoolIds);
        Map<UUID, MouDocument> currentByContract =
                currentDocuments(slice.stream().map(MouApproval::getContractId).toList());
        Map<UUID, String> names = namesOf(slice.stream()
                .map(MouApproval::getSubmittedBy)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet()));
        List<PendingRow> rows = new ArrayList<>();
        for (MouApproval a : slice) {
            Contract c = contractById.get(a.getContractId());
            SchoolInfo school = schoolInfo.get(c.getSchoolId());
            ManagerRef manager = managerOf.get(c.getSchoolId());
            rows.add(new PendingRow(
                    c.getId(),
                    c.getSchoolId(),
                    school == null ? "?" : school.name(),
                    manager == null ? null : manager.displayName(),
                    a.getRound(),
                    person(a.getSubmittedBy(), names),
                    a.getSubmittedAt(),
                    currentByContract.containsKey(c.getId()) ? "ATTACHED" : "NONE",
                    c.getTeacherCount(),
                    c.getSalaryMode() == null ? null : c.getSalaryMode().name(),
                    c.getRate(),
                    c.getStartsOn(),
                    c.getEndsOn(),
                    a.getVersion()));
        }
        return new PageResponse<>(rows, page, size, visible.size());
    }

    // ------------------------------------------------------------------ helpers

    private ApprovalSummaryDto summary(
            Contract contract,
            MouApproval approval,
            MouDocument current,
            boolean hasDocument,
            Set<Role> roles,
            Map<UUID, String> names) {
        MouApprovalStatus status = approval == null ? null : approval.getStatus();
        MouShownState shown = MouShownState.derive(contract.getState(), status, current != null);
        DocumentSummary doc = new DocumentSummary(
                current == null ? "NONE" : "ATTACHED", current == null ? null : documentDto(current, true, names));
        return new ApprovalSummaryDto(
                contract.getId(),
                shown.name(),
                approval == null ? 0 : approval.getRound(),
                approval == null ? null : person(approval.getSubmittedBy(), names),
                approval == null ? null : approval.getSubmittedAt(),
                approval == null ? null : person(approval.getDecidedBy(), names),
                approval == null ? null : approval.getDecidedAt(),
                approval == null ? null : approval.getDecisionReason(),
                approval != null && approval.isSelfDecided(),
                doc,
                approval == null ? 0 : approval.getVersion(),
                MouAllowedActions.of(roles, access.guard(), contract.getState(), status, hasDocument));
    }

    private Map<UUID, MouDocument> currentDocuments(Collection<UUID> contractIds) {
        if (contractIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, MouDocument> current = new HashMap<>();
        documents.findByContractIdInAndRemovedAtIsNull(contractIds)
                .forEach(d -> current.merge(
                        d.getContractId(), d, (x, y) -> x.getVersionNo() >= y.getVersionNo() ? x : y));
        return current;
    }

    private static void collectUsers(Set<UUID> ids, MouApproval a) {
        if (a == null) {
            return;
        }
        if (a.getSubmittedBy() != null) {
            ids.add(a.getSubmittedBy());
        }
        if (a.getDecidedBy() != null) {
            ids.add(a.getDecidedBy());
        }
    }

    private Map<UUID, String> namesOf(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return users.findAllById(ids).stream().collect(Collectors.toMap(AppUser::getId, AppUser::getDisplayName));
    }

    private static PersonRef person(UUID id, Map<UUID, String> names) {
        return id == null ? null : new PersonRef(id, names.getOrDefault(id, "Unknown"));
    }

    private static DocumentDto documentDto(MouDocument d, boolean current, Map<UUID, String> names) {
        RemovedInfo removed = d.isRemoved()
                ? new RemovedInfo(person(d.getRemovedBy(), names), d.getRemovedAt(), d.getRemovalReason())
                : null;
        return new DocumentDto(
                d.getId(),
                d.getVersionNo(),
                d.getFileName(),
                d.getContentType(),
                d.getSizeBytes(),
                person(d.getAddedBy(), names),
                d.getAddedAt(),
                current && !d.isRemoved(),
                removed);
    }
}
