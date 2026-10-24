package com.hls.leave.manager.internal;

import com.hls.identity.permissions.PermissionAction;
import com.hls.identity.user.Role;
import com.hls.leave.manager.internal.ManagerLeaveRequest.Status;
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

/** Manager leave requests waiting for a decision (inbox kind {@code MANAGER_LEAVE}). */
@Component
class ManagerLeaveApprovals implements ApprovalSource {

    static final String KIND = "MANAGER_LEAVE";

    private final ManagerLeaveRequestRepository requests;
    private final ManagerQueries managers;

    ManagerLeaveApprovals(ManagerLeaveRequestRepository requests, ManagerQueries managers) {
        this.requests = requests;
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
        return requests.findByStatusOrderByRequestedAtAsc(Status.PENDING).stream()
                .map(r -> {
                    ManagerQueries.ManagerRef m = byId.get(r.getManagerId());
                    String subject = r.getFirstDate().equals(r.getLastDate())
                            ? "Leave on " + r.getFirstDate()
                            : "Leave " + r.getFirstDate() + " to " + r.getLastDate();
                    return new ApprovalItem(
                            KIND, r.getId(), m == null ? null : m.userId(), m == null ? "Unknown" : m.displayName(), subject,
                            LocalDate.ofInstant(r.getRequestedAt(), ZoneOffset.UTC),
                            Link.to("/operations/manager-leave", "id", r.getId().toString()));
                })
                .toList();
    }
}
