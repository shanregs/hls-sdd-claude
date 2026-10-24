package com.hls.tasks.internal;

import com.hls.identity.permissions.PermissionAction;
import com.hls.identity.user.AppUser;
import com.hls.identity.user.AppUserRepository;
import com.hls.identity.user.Role;
import com.hls.reporting.api.ApprovalItem;
import com.hls.reporting.api.ApprovalSource;
import com.hls.reporting.api.Link;
import com.hls.school.api.CallerContext;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Completed tasks waiting for verification (inbox kind {@code TASK_VERIFICATION}), as the Tasks list "awaiting" view shows them. */
@Component
class TaskApprovals implements ApprovalSource {

    static final String KIND = "TASK_VERIFICATION";

    private final TaskRepository tasks;
    private final AppUserRepository users;

    TaskApprovals(TaskRepository tasks, AppUserRepository users) {
        this.tasks = tasks;
        this.users = users;
    }

    @Override
    public String kind() {
        return KIND;
    }

    @Override
    public String permissionModule() {
        return "TASKS";
    }

    @Override
    public PermissionAction action() {
        return PermissionAction.APPROVE;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ApprovalItem> pending(UUID userId, Set<Role> roles) {
        boolean orgWide = CallerContext.isOrgWide(roles);
        Specification<Task> awaiting = (root, q, cb) -> cb.equal(root.get("status"), TaskStatus.COMPLETED);
        Specification<Task> scoped = orgWide ? awaiting : awaiting.and((root, q, cb) -> cb.equal(root.get("assigneeUserId"), userId));
        List<Task> found = tasks.findAll(scoped);
        Map<UUID, String> names = users.findAllById(found.stream().map(Task::getAssigneeUserId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(AppUser::getId, AppUser::getDisplayName, (a, b) -> a));
        return found.stream()
                .map(t -> new ApprovalItem(
                        KIND, t.getId(), t.getAssigneeUserId(), names.getOrDefault(t.getAssigneeUserId(), "Unknown"),
                        t.getTitle(),
                        LocalDate.ofInstant(t.getCompletedAt() != null ? t.getCompletedAt() : t.getCreatedAt(), ZoneOffset.UTC),
                        Link.to("/operations/tasks/" + t.getId())))
                .toList();
    }
}
