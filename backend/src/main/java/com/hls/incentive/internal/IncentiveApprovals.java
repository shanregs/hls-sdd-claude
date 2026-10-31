package com.hls.incentive.internal;

import com.hls.identity.permissions.PermissionAction;
import com.hls.identity.user.Role;
import com.hls.incentive.api.IncentiveSummary;
import com.hls.reporting.api.ApprovalItem;
import com.hls.reporting.api.ApprovalSource;
import com.hls.reporting.api.Link;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Incentives waiting for a decision, for the Director's approvals inbox (inbox kind {@code MANAGER_INCENTIVE}, spec 030 FR-014). */
@Component
class IncentiveApprovals implements ApprovalSource {

    static final String KIND = "MANAGER_INCENTIVE";

    private final IncentiveSummary summary;

    IncentiveApprovals(IncentiveSummary summary) {
        this.summary = summary;
    }

    @Override
    public String kind() {
        return KIND;
    }

    @Override
    public String permissionModule() {
        return "MANAGER_INCENTIVES";
    }

    @Override
    public PermissionAction action() {
        return PermissionAction.APPROVE;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ApprovalItem> pending(UUID userId, Set<Role> roles) {
        return summary.pendingFor(userId, roles).stream()
                .map(i -> new ApprovalItem(
                        KIND,
                        i.incentiveId(),
                        i.earnerUserId(),
                        i.earnerName(),
                        "Incentive for " + i.schoolName() + " (Rs " + i.amount().setScale(2).toPlainString() + ")",
                        i.closureDate(),
                        Link.to("/operations/incentives/" + i.incentiveId())))
                .toList();
    }
}
