package com.hls.schoolbilling.internal;

import com.hls.schoolbilling.api.MouApprovalState;
import com.hls.schoolbilling.api.MouApprovals;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The approval state of the MoU in effect, for other modules (spec 031 US4). The MoU is the live contract whose dates
 * include the date, the same one {@code SchoolContracts.contractOf} returns. Unscoped by design: callers are modules.
 */
@Service
class MouApprovalsImpl implements MouApprovals {

    private final ContractRepository contracts;
    private final MouApprovalRepository approvals;
    private final MouDocumentRepository documents;

    MouApprovalsImpl(ContractRepository contracts, MouApprovalRepository approvals, MouDocumentRepository documents) {
        this.contracts = contracts;
        this.approvals = approvals;
        this.documents = documents;
    }

    @Override
    @Transactional(readOnly = true)
    public MouApprovalState stateOn(UUID schoolId, LocalDate date) {
        return stateOfAll(List.of(schoolId), date).get(schoolId);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, MouApprovalState> stateOfAll(Collection<UUID> schoolIds, LocalDate date) {
        Map<UUID, MouApprovalState> result = new LinkedHashMap<>();
        if (schoolIds.isEmpty()) {
            return result;
        }
        Map<UUID, Contract> inEffect = new HashMap<>();
        for (Contract c : contracts.liveOverlapping(schoolIds, date, date)) {
            inEffect.putIfAbsent(c.getSchoolId(), c);
        }
        List<UUID> contractIds = inEffect.values().stream().map(Contract::getId).toList();
        Map<UUID, MouApproval> approvalOf = new HashMap<>();
        Set<UUID> withDocument = new HashSet<>();
        if (!contractIds.isEmpty()) {
            approvals.findByContractIdIn(contractIds).forEach(a -> approvalOf.put(a.getContractId(), a));
            withDocument.addAll(documents.contractsWithDocument(contractIds));
        }
        for (UUID schoolId : schoolIds) {
            Contract c = inEffect.get(schoolId);
            if (c == null) {
                result.put(schoolId, new MouApprovalState(schoolId, null, "NO_MOU", null, false, false));
                continue;
            }
            MouApproval a = approvalOf.get(c.getId());
            boolean hasDoc = withDocument.contains(c.getId());
            MouShownState shown = MouShownState.derive(c.getState(), a == null ? null : a.getStatus(), hasDoc);
            boolean decidedApproval = a != null && a.getStatus() == MouApprovalStatus.APPROVED;
            result.put(
                    schoolId,
                    new MouApprovalState(
                            schoolId,
                            c.getId(),
                            shown.name(),
                            decidedApproval ? a.getDecidedAt() : null,
                            hasDoc,
                            shown.countsAsApproved()));
        }
        return result;
    }
}
