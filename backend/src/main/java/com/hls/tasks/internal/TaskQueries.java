package com.hls.tasks.internal;

import com.hls.designation.api.BusinessDate;
import com.hls.files.api.FileStore;
import com.hls.identity.user.AppUser;
import com.hls.identity.user.AppUserRepository;
import com.hls.organization.api.ManagerQueries;
import com.hls.school.api.InvalidInputException;
import com.hls.school.api.PageResponse;
import com.hls.tasks.api.TaskType;
import com.hls.tasks.internal.TaskService.Caller;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reads for the task screens: the lists (today, upcoming, overdue, awaiting, done), counts and the detail. */
@Service
public class TaskQueries {

    /** The owner type under which evidence is kept in the shared file store. */
    public static final String FILE_OWNER = "TASK";

    public static final int MAX_PAGE_SIZE = 100;

    public record Row(
            UUID id,
            String type,
            String typeLabel,
            String title,
            String status,
            LocalDate dueOn,
            UUID assigneeUserId,
            String assigneeName,
            boolean assigneeActive,
            String linkedType,
            UUID linkedId,
            String linkedLabel,
            String place,
            boolean overdue,
            long daysOverdue,
            boolean escalated) {}

    public record Summary(long today, long upcoming, long overdue, long awaiting, long escalated, long done) {}

    public record HistoryLine(String event, UUID actorUserId, String actorName, Instant at, String before, String after, String note) {}

    public record EvidenceFile(UUID fileId, String name, String contentType, long sizeBytes, Instant addedAt, UUID addedBy) {}

    public record Actions(
            boolean acknowledge,
            boolean start,
            boolean complete,
            boolean overdueReason,
            boolean addEvidence,
            boolean verify,
            boolean sendBack,
            boolean edit,
            boolean cancel) {}

    public record Detail(
            Row task,
            String description,
            UUID createdBy,
            String createdByName,
            int escalateAfterDays,
            String source,
            String overdueReason,
            String completionNote,
            Instant completedAt,
            Instant verifiedAt,
            String verifiedByName,
            Instant escalatedAt,
            String cancelReason,
            UUID parentTaskId,
            List<UUID> followUpTaskIds,
            List<HistoryLine> history,
            List<EvidenceFile> evidence,
            Actions actions,
            Long version) {}

    public record TypeOption(String value, String label) {}

    public record Assignee(UUID userId, String name) {}

    private final TaskRepository tasks;
    private final TaskHistoryRepository history;
    private final TaskEvidenceRepository evidence;
    private final TaskService service;
    private final AppUserRepository users;
    private final ManagerQueries managers;
    private final FileStore files;
    private final BusinessDate businessDate;

    public TaskQueries(
            TaskRepository tasks,
            TaskHistoryRepository history,
            TaskEvidenceRepository evidence,
            TaskService service,
            AppUserRepository users,
            ManagerQueries managers,
            FileStore files,
            BusinessDate businessDate) {
        this.tasks = tasks;
        this.history = history;
        this.evidence = evidence;
        this.service = service;
        this.users = users;
        this.managers = managers;
        this.files = files;
        this.businessDate = businessDate;
    }

    public List<TypeOption> types() {
        List<TypeOption> options = new ArrayList<>();
        for (TaskType type : TaskType.values()) {
            options.add(new TypeOption(type.name(), type.label()));
        }
        return options;
    }

    /** Active Managers the caller may give a task to: all for Admin and Director, only themselves for a Manager. */
    @Transactional(readOnly = true)
    public List<Assignee> assignees(Caller caller) {
        if (!caller.orgWide()) {
            return managers.managerByUserId(caller.userId())
                    .filter(ManagerQueries.ManagerRef::active)
                    .map(m -> List.of(new Assignee(caller.userId(), m.displayName())))
                    .orElse(List.of());
        }
        List<Assignee> result = new ArrayList<>();
        for (UUID userId : users.findAll().stream().filter(AppUser::isActive).map(AppUser::getId).toList()) {
            managers.managerByUserId(userId)
                    .filter(ManagerQueries.ManagerRef::active)
                    .ifPresent(m -> result.add(new Assignee(userId, m.displayName())));
        }
        result.sort(java.util.Comparator.comparing(Assignee::name, String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    @Transactional(readOnly = true)
    public PageResponse<Row> list(Caller caller, String view, UUID assignee, String type, String status, int page, int size) {
        LocalDate today = businessDate.today();
        String v = view == null || view.isBlank() ? "all" : view.toLowerCase();
        Specification<Task> spec = scope(caller, assignee).and(view(v, today));
        if (type != null && !type.isBlank()) {
            TaskType parsed = parse(TaskType.class, type, "task type");
            spec = spec.and((root, q, cb) -> cb.equal(root.get("type"), parsed));
        }
        if (status != null && !status.isBlank()) {
            TaskStatus parsed = parse(TaskStatus.class, status, "status");
            spec = spec.and((root, q, cb) -> cb.equal(root.get("status"), parsed));
        }
        Sort sort = v.equals("done")
                ? Sort.by(Sort.Order.desc("updatedAt"))
                : Sort.by(Sort.Order.asc("dueOn"), Sort.Order.asc("createdAt"));
        var result = tasks.findAll(spec, PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE), sort));
        return new PageResponse<>(rows(result.getContent(), today), result.getNumber(), result.getSize(), result.getTotalElements());
    }

    @Transactional(readOnly = true)
    public Summary summary(Caller caller, UUID assignee) {
        LocalDate today = businessDate.today();
        Specification<Task> scope = scope(caller, assignee);
        return new Summary(
                tasks.count(scope.and(view("today", today))),
                tasks.count(scope.and(view("upcoming", today))),
                tasks.count(scope.and(view("overdue", today))),
                tasks.count(scope.and(view("awaiting", today))),
                tasks.count(scope.and(view("escalated", today))),
                tasks.count(scope.and(view("done", today))));
    }

    @Transactional(readOnly = true)
    public Detail detail(Caller caller, UUID id) {
        Task task = service.visible(caller, id);
        LocalDate today = businessDate.today();
        Row row = rows(List.of(task), today).get(0);
        List<TaskHistory> lines = history.findByTaskIdOrderByAtAsc(id);
        Map<UUID, String> names = names(lines.stream().map(TaskHistory::getActorUserId).collect(Collectors.toSet()));
        List<HistoryLine> historyLines = lines.stream()
                .map(h -> new HistoryLine(
                        h.getEvent(),
                        h.getActorUserId(),
                        h.getActorUserId() == null ? "System" : names.getOrDefault(h.getActorUserId(), "Unknown"),
                        h.getAt(),
                        h.getBeforeValue(),
                        h.getAfterValue(),
                        h.getNote()))
                .toList();
        List<EvidenceFile> files = new ArrayList<>();
        for (TaskEvidence e : evidence.findByTaskIdOrderByAddedAtAsc(id)) {
            this.files.find(e.getFileId())
                    .ifPresent(f -> files.add(new EvidenceFile(f.id(), f.name(), f.contentType(), f.sizeBytes(), e.getAddedAt(), e.getAddedBy())));
        }
        Map<UUID, String> people = names(java.util.Arrays.asList(task.getCreatedBy(), task.getVerifiedBy()));
        List<UUID> followUps = tasks.findByParentTaskIdOrderByCreatedAt(id).stream().map(Task::getId).toList();
        return new Detail(
                row,
                task.getDescription(),
                task.getCreatedBy(),
                task.getCreatedBy() == null ? "System" : people.get(task.getCreatedBy()),
                task.getEscalateAfterDays(),
                task.getSource(),
                task.getOverdueReason(),
                task.getCompletionNote(),
                task.getCompletedAt(),
                task.getVerifiedAt(),
                task.getVerifiedBy() == null ? null : people.get(task.getVerifiedBy()),
                task.getEscalatedAt(),
                task.getCancelReason(),
                task.getParentTaskId(),
                followUps,
                historyLines,
                files,
                actions(caller, task, today, evidence.countByTaskId(id)),
                task.getVersion());
    }

    // ------------------------------------------------------------------ internals

    private Actions actions(Caller caller, Task task, LocalDate today, long evidenceCount) {
        boolean assignee = task.getAssigneeUserId().equals(caller.userId());
        TaskStatus s = task.getStatus();
        boolean overdue = task.isOverdueOn(today);
        boolean openOrDone = s.isOpen() || s == TaskStatus.COMPLETED;
        return new Actions(
                assignee && s == TaskStatus.ASSIGNED,
                assignee && s == TaskStatus.ACKNOWLEDGED,
                assignee && (s == TaskStatus.ACKNOWLEDGED || s == TaskStatus.IN_PROGRESS),
                assignee && overdue,
                assignee && openOrDone && s != TaskStatus.ASSIGNED && evidenceCount < TaskService.MAX_EVIDENCE,
                !assignee && s == TaskStatus.COMPLETED,
                !assignee && s == TaskStatus.COMPLETED,
                caller.orgWide() && s.ordinal() <= TaskStatus.IN_PROGRESS.ordinal(),
                caller.orgWide() && s.ordinal() <= TaskStatus.IN_PROGRESS.ordinal());
    }

    private Specification<Task> scope(Caller caller, UUID assignee) {
        UUID only = caller.orgWide() ? assignee : caller.userId();
        return (root, q, cb) -> only == null ? cb.conjunction() : cb.equal(root.get("assigneeUserId"), only);
    }

    private static Specification<Task> view(String view, LocalDate today) {
        return (root, q, cb) -> {
            List<Predicate> all = new ArrayList<>();
            switch (view) {
                case "today" -> {
                    all.add(root.get("status").in(TaskStatus.OPEN));
                    all.add(cb.equal(root.get("dueOn"), today));
                }
                case "upcoming" -> {
                    all.add(root.get("status").in(TaskStatus.OPEN));
                    all.add(cb.greaterThan(root.<LocalDate>get("dueOn"), today));
                }
                case "overdue" -> {
                    all.add(root.get("status").in(TaskStatus.OPEN));
                    all.add(cb.lessThan(root.<LocalDate>get("dueOn"), today));
                }
                case "escalated" -> {
                    all.add(root.get("status").in(TaskStatus.OPEN));
                    all.add(cb.isNotNull(root.get("escalatedAt")));
                }
                case "awaiting" -> all.add(cb.equal(root.get("status"), TaskStatus.COMPLETED));
                case "done" -> all.add(cb.equal(root.get("status"), TaskStatus.VERIFIED));
                case "all" -> {}
                default -> throw new InvalidInputException("The view must be today, upcoming, overdue, awaiting, done or all.");
            }
            return cb.and(all.toArray(new Predicate[0]));
        };
    }

    private List<Row> rows(Collection<Task> list, LocalDate today) {
        Map<UUID, AppUser> assignees = users.findAllById(list.stream().map(Task::getAssigneeUserId).distinct().toList()).stream()
                .collect(Collectors.toMap(AppUser::getId, u -> u));
        return list.stream()
                .map(t -> {
                    AppUser user = assignees.get(t.getAssigneeUserId());
                    boolean overdue = t.isOverdueOn(today);
                    return new Row(
                            t.getId(),
                            t.getType().name(),
                            t.getType().label(),
                            t.getTitle(),
                            t.getStatus().name(),
                            t.getDueOn(),
                            t.getAssigneeUserId(),
                            user == null ? "Unknown" : user.getDisplayName(),
                            user != null && user.isActive(),
                            t.getLinkedType(),
                            t.getLinkedId(),
                            t.getLinkedLabel(),
                            t.getPlace(),
                            overdue,
                            overdue ? ChronoUnit.DAYS.between(t.getDueOn(), today) : 0,
                            t.getEscalatedAt() != null);
                })
                .toList();
    }

    private Map<UUID, String> names(Collection<UUID> ids) {
        List<UUID> real = ids.stream().filter(java.util.Objects::nonNull).distinct().toList();
        Map<UUID, String> result = new HashMap<>();
        users.findAllById(real).forEach(u -> result.put(u.getId(), u.getDisplayName()));
        return result;
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String value, String what) {
        try {
            return Enum.valueOf(type, value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new InvalidInputException("Unknown " + what + ": " + value);
        }
    }
}
