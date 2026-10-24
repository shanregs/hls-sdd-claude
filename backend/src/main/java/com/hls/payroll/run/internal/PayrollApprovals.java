package com.hls.payroll.run.internal;

import com.hls.identity.permissions.PermissionAction;
import com.hls.identity.user.AppUser;
import com.hls.identity.user.AppUserRepository;
import com.hls.identity.user.Role;
import com.hls.reporting.api.ApprovalItem;
import com.hls.reporting.api.ApprovalSource;
import com.hls.reporting.api.Link;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Pay runs waiting for approval (inbox kind {@code PAYROLL_RUN}); the inbox drops a run the caller submitted. */
@Component
class PayrollApprovals implements ApprovalSource {

    static final String KIND = "PAYROLL_RUN";

    private final PayRunRepository runs;
    private final AppUserRepository users;

    PayrollApprovals(PayRunRepository runs, AppUserRepository users) {
        this.runs = runs;
        this.users = users;
    }

    @Override
    public String kind() {
        return KIND;
    }

    @Override
    public String permissionModule() {
        return "PAYROLL";
    }

    @Override
    public PermissionAction action() {
        return PermissionAction.APPROVE;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ApprovalItem> pending(UUID userId, Set<Role> roles) {
        return runs.search(PayRun.Status.AWAITING_APPROVAL, org.springframework.data.domain.PageRequest.of(0, 200)).stream()
                .map(r -> new ApprovalItem(
                        KIND, r.getId(), r.getSubmittedBy(), nameOf(r.getSubmittedBy()),
                        "Pay run for " + r.getMonth() + (r.getKind() == PayRun.Kind.PRIMARY ? "" : " (supplementary)"),
                        r.getSubmittedAt() == null ? null : LocalDate.ofInstant(r.getSubmittedAt(), ZoneOffset.UTC),
                        Link.to("/operations/payroll/" + r.getId())))
                .toList();
    }

    private String nameOf(UUID userId) {
        if (userId == null) {
            return "Unknown";
        }
        return users.findById(userId).map(AppUser::getDisplayName).orElse("Unknown");
    }
}
