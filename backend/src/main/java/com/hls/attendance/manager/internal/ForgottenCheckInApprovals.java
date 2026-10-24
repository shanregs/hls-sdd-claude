package com.hls.attendance.manager.internal;

import com.hls.identity.permissions.PermissionAction;
import com.hls.identity.user.Role;
import com.hls.organization.api.ManagerQueries;
import com.hls.reporting.api.ApprovalItem;
import com.hls.reporting.api.ApprovalSource;
import com.hls.reporting.api.Link;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Forgotten check-in requests waiting for a decision (inbox kind {@code FORGOTTEN_CHECK_IN}); decided with Manager Leave Approve. */
@Component
class ForgottenCheckInApprovals implements ApprovalSource {

    static final String KIND = "FORGOTTEN_CHECK_IN";

    private final ManagerRegularizationRepository regularizations;
    private final ManagerQueries managers;

    ForgottenCheckInApprovals(ManagerRegularizationRepository regularizations, ManagerQueries managers) {
        this.regularizations = regularizations;
        this.managers = managers;
    }

    @Override
    public String kind() {
        return KIND;
    }

    @Override
    public String permissionModule() {
        return "MANAGER_LEAVE";
    }

    @Override
    public PermissionAction action() {
        return PermissionAction.APPROVE;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ApprovalItem> pending(UUID userId, Set<Role> roles) {
        Map<UUID, ManagerQueries.ManagerRef> byId = managers.allManagers().stream()
                .collect(Collectors.toMap(ManagerQueries.ManagerRef::id, Function.identity(), (a, b) -> a));
        return regularizations.findByStatusOrderByRequestedAtAsc(ManagerRegularization.Status.PENDING).stream()
                .map(r -> {
                    ManagerQueries.ManagerRef m = byId.get(r.getManagerId());
                    return new ApprovalItem(
                            KIND, r.getId(), m == null ? null : m.userId(), m == null ? "Unknown" : m.displayName(),
                            "Forgotten check-in on " + r.getDayDate(),
                            LocalDate.ofInstant(r.getRequestedAt(), ZoneOffset.UTC),
                            Link.to("/operations/manager-attendance", "view", "regularizations"));
                })
                .toList();
    }
}
