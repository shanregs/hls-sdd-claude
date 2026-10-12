package com.hls.tasks.internal;

import com.hls.designation.api.BusinessDate;
import com.hls.files.api.FileStore;
import com.hls.files.api.FileStore.FileContent;
import com.hls.identity.user.AppUser;
import com.hls.identity.user.AppUserRepository;
import com.hls.identity.user.Role;
import com.hls.organization.api.ManagerQueries;
import com.hls.school.api.CallerContext;
import com.hls.school.api.ConflictException;
import com.hls.school.api.ForbiddenFieldException;
import com.hls.school.api.InvalidInputException;
import com.hls.school.api.NotFoundException;
import com.hls.school.api.StaleVersion;
import com.hls.tasks.api.TaskAssigned;
import com.hls.tasks.api.TaskCreation;
import com.hls.tasks.api.TaskType;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The life of a task (spec 025): create, acknowledge, start, complete with evidence, verify or send back, record why it
 * is overdue, edit, reassign and cancel. Also the shared creation interface other modules call (contract C2). Every
 * change writes the task history and Change History; the server alone decides who may do what.
 */
@Service
public class TaskService implements TaskCreation {

    public static final int DEFAULT_ESCALATE_DAYS = 3;
    public static final int MAX_EVIDENCE = 5;

    /** The person doing something: their user id and roles. */
    public record Caller(UUID userId, Set<Role> roles) {
        boolean orgWide() {
            return CallerContext.isOrgWide(roles);
        }
    }

    public record CreateRequest(
            TaskType type,
            String title,
            String description,
            UUID assigneeUserId,
            LocalDate dueOn,
            Integer escalateAfterDays,
            String linkedType,
            UUID linkedId,
            String linkedLabel,
            String place) {}

    public record UpdateRequest(
            String title,
            String description,
            UUID assigneeUserId,
            LocalDate dueOn,
            Integer escalateAfterDays,
            String place,
            Long version) {}

    private final TaskRepository tasks;
    private final TaskEvidenceRepository evidence;
    private final TaskRecorder recorder;
    private final ManagerQueries managers;
    private final AppUserRepository users;
    private final FileStore files;
    private final ApplicationEventPublisher events;
    private final BusinessDate businessDate;
    private final Clock clock;

    public TaskService(
            TaskRepository tasks,
            TaskEvidenceRepository evidence,
            TaskRecorder recorder,
            ManagerQueries managers,
            AppUserRepository users,
            FileStore files,
            ApplicationEventPublisher events,
            BusinessDate businessDate,
            Clock clock) {
        this.tasks = tasks;
        this.evidence = evidence;
        this.recorder = recorder;
        this.managers = managers;
        this.users = users;
        this.files = files;
        this.events = events;
        this.businessDate = businessDate;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ create

    /** A person creates a task: Admin and Director for any Manager, a Manager only for themselves. */
    @Transactional
    public UUID create(Caller caller, CreateRequest request) {
        if (request.assigneeUserId() == null) {
            throw new InvalidInputException("Choose who the task is for.");
        }
        if (!caller.orgWide() && !caller.userId().equals(request.assigneeUserId())) {
            throw new ForbiddenFieldException("You can create tasks only for yourself.");
        }
        if (request.dueOn() == null) {
            throw new InvalidInputException("The due date is required.");
        }
        if (request.dueOn().isBefore(businessDate.today())) {
            throw new InvalidInputException("The due date cannot be in the past.");
        }
        Task task = build(request, caller.userId(), "MANUAL", null, null);
        return task.getId();
    }

    /** Contract C2: idempotent per source key while a task for the key is still live. */
    @Override
    @Transactional
    public TaskRef createFollowUp(FollowUpRequest request) {
        if (request == null || request.source() == null || request.source().isBlank()) {
            throw new InvalidInputException("The calling module's source is required.");
        }
        String source = request.source().trim();
        if (source.length() > 40) {
            throw new InvalidInputException("The source must be at most 40 characters.");
        }
        String key = request.sourceKey() == null || request.sourceKey().isBlank() ? null : request.sourceKey().trim();
        if (key != null) {
            if (key.length() > 120) {
                throw new InvalidInputException("The source key must be at most 120 characters.");
            }
            var existing = tasks.findFirstBySourceAndSourceKeyAndStatusIn(source, key, TaskStatus.LIVE);
            if (existing.isPresent()) {
                return new TaskRef(existing.get().getId(), false, existing.get().getStatus().name());
            }
        }
        if (request.dueOn() == null) {
            throw new InvalidInputException("The due date is required.");
        }
        CreateRequest create = new CreateRequest(
                request.type() == null ? TaskType.FOLLOW_UP : request.type(),
                request.title(),
                request.description(),
                request.assigneeUserId(),
                request.dueOn(),
                request.escalateAfterDays(),
                request.linkedType(),
                request.linkedId(),
                request.linkedLabel(),
                request.place());
        // two simultaneous first calls for one key: the unique index refuses the second, which can simply call again
        Task task = build(create, request.createdByUserId(), source, key, null);
        return new TaskRef(task.getId(), true, task.getStatus().name());
    }

    private Task build(CreateRequest request, UUID actor, String source, String sourceKey, UUID parent) {
        if (request.type() == null) {
            throw new InvalidInputException("Choose the type of task.");
        }
        String title = cleanTitle(request.title());
        String description = clean(request.description(), 2000, "The description");
        requireActiveManager(request.assigneeUserId());
        int days = escalationDays(request.escalateAfterDays());
        String linkedType = clean(request.linkedType(), 40, "The linked record type");
        String linkedLabel = clean(request.linkedLabel(), 160, "The linked record label");
        if (request.linkedId() != null && linkedType == null) {
            throw new InvalidInputException("A linked record needs its type.");
        }
        String place = clean(request.place(), 160, "The place");
        Task task = new Task(request.type(), title, request.assigneeUserId(), actor, request.dueOn(), days, source, clock.instant());
        task.describe(description);
        task.link(linkedType, request.linkedId(), linkedLabel, place);
        task.fromSource(sourceKey, parent);
        tasks.save(task);
        recorder.created(task, actor, request.type() + " for " + request.assigneeUserId() + " due " + request.dueOn() + " (" + source + ")");
        if (!request.assigneeUserId().equals(actor)) {
            events.publishEvent(new TaskAssigned(task.getId(), task.getTitle(), task.getAssigneeUserId(), actor, task.getDueOn()));
        }
        return task;
    }

    // ------------------------------------------------------------------ the assignee

    @Transactional
    public void acknowledge(Caller caller, UUID id) {
        Task task = visible(caller, id);
        requireAssignee(task, caller);
        requireStatus(task, "acknowledged", TaskStatus.ASSIGNED);
        move(task, caller, TaskStatus.ACKNOWLEDGED, "ACKNOWLEDGED", null);
    }

    @Transactional
    public void start(Caller caller, UUID id) {
        Task task = visible(caller, id);
        requireAssignee(task, caller);
        requireStatus(task, "started", TaskStatus.ACKNOWLEDGED);
        move(task, caller, TaskStatus.IN_PROGRESS, "STARTED", null);
    }

    /** Records why an overdue task is late; a follow-up date creates a linked Follow-up task. Returns the follow-up id or null. */
    @Transactional
    public UUID overdueReason(Caller caller, UUID id, String reason, LocalDate followUpOn) {
        Task task = visible(caller, id);
        requireAssignee(task, caller);
        if (!task.isOverdueOn(businessDate.today())) {
            throw new ConflictException("This task is not overdue.");
        }
        String text = required(reason, 5, 500, "The reason");
        if (followUpOn != null && followUpOn.isBefore(businessDate.today())) {
            throw new InvalidInputException("The follow-up date cannot be in the past.");
        }
        String before = task.getOverdueReason();
        task.recordOverdueReason(text, clock.instant());
        recorder.change(task, "OVERDUE_REASON", caller.userId(), "overdueReason", before, text, followUpOn == null ? null : "Follow-up on " + followUpOn);
        UUID followUp = null;
        if (followUpOn != null) {
            CreateRequest create = new CreateRequest(
                    TaskType.FOLLOW_UP,
                    "Follow-up: " + task.getTitle(),
                    "Follow-up on an overdue task: " + text,
                    task.getAssigneeUserId(),
                    followUpOn,
                    task.getEscalateAfterDays(),
                    task.getLinkedType(),
                    task.getLinkedId(),
                    task.getLinkedLabel(),
                    task.getPlace());
            followUp = build(create, caller.userId(), "MANUAL", null, task.getId()).getId();
        }
        return followUp;
    }

    @Transactional
    public void complete(Caller caller, UUID id, String note) {
        Task task = visible(caller, id);
        requireAssignee(task, caller);
        if (task.getStatus() != TaskStatus.ACKNOWLEDGED && task.getStatus() != TaskStatus.IN_PROGRESS) {
            throw new ConflictException("A task can be completed only after it is acknowledged. It is " + label(task.getStatus()) + ".");
        }
        String text = required(note, 10, 2000, "The completion note");
        if (task.isOverdueOn(businessDate.today()) && (task.getOverdueReason() == null || task.getOverdueReason().isBlank())) {
            throw new InvalidInputException("This task is overdue. Record why it is late before completing it.");
        }
        TaskStatus before = task.getStatus();
        task.complete(text, clock.instant());
        recorder.change(task, "COMPLETED", caller.userId(), "status", before, task.getStatus(), text);
    }

    @Transactional
    public void addEvidence(Caller caller, UUID id, String name, byte[] content) {
        Task task = visible(caller, id);
        requireAssignee(task, caller);
        if (!task.getStatus().isOpen() && task.getStatus() != TaskStatus.COMPLETED) {
            throw new ConflictException("Evidence cannot be added to a " + label(task.getStatus()) + " task.");
        }
        if (task.getStatus() == TaskStatus.ASSIGNED) {
            throw new ConflictException("Acknowledge the task before adding evidence.");
        }
        if (evidence.countByTaskId(id) >= MAX_EVIDENCE) {
            throw new ConflictException("A task can have at most " + MAX_EVIDENCE + " evidence files.");
        }
        FileStore.FileRef stored = files.store(caller.userId(), TaskQueries.FILE_OWNER, id, name, content);
        evidence.save(new TaskEvidence(id, stored.id(), caller.userId(), clock.instant()));
        recorder.note(task, "EVIDENCE_ADDED", caller.userId(), stored.name() + " (" + stored.sizeBytes() + " bytes)");
    }

    /** The evidence file, if the caller can see its task; anyone else gets not found. */
    @Transactional(readOnly = true)
    public FileContent download(Caller caller, UUID fileId) {
        TaskEvidence link = evidence.findByFileId(fileId).orElseThrow(() -> new NotFoundException("File not found."));
        visible(caller, link.getTaskId());
        return files.open(fileId).orElseThrow(() -> new NotFoundException("File not found."));
    }

    // ------------------------------------------------------------------ the verifier

    @Transactional
    public void verify(Caller caller, UUID id) {
        Task task = visible(caller, id);
        requireNotAssignee(task, caller, "verify");
        requireStatus(task, "verified", TaskStatus.COMPLETED);
        task.verify(caller.userId(), clock.instant());
        recorder.change(task, "VERIFIED", caller.userId(), "status", TaskStatus.COMPLETED, TaskStatus.VERIFIED, null);
    }

    @Transactional
    public void sendBack(Caller caller, UUID id, String reason) {
        Task task = visible(caller, id);
        requireNotAssignee(task, caller, "send back");
        requireStatus(task, "sent back", TaskStatus.COMPLETED);
        String text = required(reason, 3, 500, "The reason");
        task.sendBack(clock.instant());
        recorder.change(task, "SENT_BACK", caller.userId(), "status", TaskStatus.COMPLETED, TaskStatus.IN_PROGRESS, text);
    }

    // ------------------------------------------------------------------ management

    @Transactional
    public void update(Caller caller, UUID id, UpdateRequest request) {
        requireOrgWide(caller, "change a task");
        Task task = visible(caller, id);
        StaleVersion.check(Task.class, id, task.getVersion(), request.version());
        if (task.getStatus().ordinal() > TaskStatus.IN_PROGRESS.ordinal()) {
            throw new ConflictException("A " + label(task.getStatus()) + " task cannot be changed.");
        }
        String title = request.title() == null ? task.getTitle() : cleanTitle(request.title());
        String description = request.description() == null ? task.getDescription() : clean(request.description(), 2000, "The description");
        UUID assignee = request.assigneeUserId() == null ? task.getAssigneeUserId() : request.assigneeUserId();
        LocalDate due = request.dueOn() == null ? task.getDueOn() : request.dueOn();
        int days = request.escalateAfterDays() == null ? task.getEscalateAfterDays() : escalationDays(request.escalateAfterDays());
        String place = request.place() == null ? task.getPlace() : clean(request.place(), 160, "The place");
        boolean reassigned = !assignee.equals(task.getAssigneeUserId());
        boolean moved = !due.equals(task.getDueOn());
        if (reassigned) {
            requireActiveManager(assignee);
        }
        if (moved && due.isBefore(businessDate.today())) {
            throw new InvalidInputException("The due date cannot be in the past.");
        }
        if (!title.equals(task.getTitle())) {
            recorder.change(task, "EDITED", caller.userId(), "title", task.getTitle(), title, null);
        }
        if (!Objects.equals(description, task.getDescription())) {
            recorder.change(task, "EDITED", caller.userId(), "description", task.getDescription(), description, null);
        }
        if (days != task.getEscalateAfterDays()) {
            recorder.change(task, "EDITED", caller.userId(), "escalateAfterDays", task.getEscalateAfterDays(), days, null);
        }
        if (!Objects.equals(place, task.getPlace())) {
            recorder.change(task, "EDITED", caller.userId(), "place", task.getPlace(), place, null);
        }
        if (moved) {
            recorder.change(task, "DUE_CHANGED", caller.userId(), "dueOn", task.getDueOn(), due, null);
        }
        if (reassigned) {
            recorder.change(task, "REASSIGNED", caller.userId(), "assigneeUserId", task.getAssigneeUserId(), assignee, null);
        }
        task.edit(title, description, assignee, due, days, place, clock.instant());
        if (reassigned && task.getStatus() != TaskStatus.ASSIGNED) {
            recorder.change(task, "REASSIGNED", caller.userId(), "status", task.getStatus(), TaskStatus.ASSIGNED, "Back to assigned for the new Manager");
            task.moveTo(TaskStatus.ASSIGNED, clock.instant());
        }
        tasks.saveAndFlush(task);
        if (reassigned) {
            events.publishEvent(new TaskAssigned(task.getId(), task.getTitle(), assignee, caller.userId(), task.getDueOn()));
        }
    }

    @Transactional
    public void cancel(Caller caller, UUID id, String reason) {
        requireOrgWide(caller, "cancel a task");
        Task task = visible(caller, id);
        if (task.getStatus() == TaskStatus.COMPLETED || task.getStatus() == TaskStatus.VERIFIED || task.getStatus() == TaskStatus.CANCELLED) {
            throw new ConflictException("A " + label(task.getStatus()) + " task cannot be cancelled.");
        }
        String text = required(reason, 3, 300, "The reason");
        TaskStatus before = task.getStatus();
        task.cancel(text, clock.instant());
        recorder.change(task, "CANCELLED", caller.userId(), "status", before, TaskStatus.CANCELLED, text);
    }

    // ------------------------------------------------------------------ helpers

    /** The task if the caller may see it: Admin and Director see all, a Manager only their own; others get not found. */
    Task visible(Caller caller, UUID id) {
        Task task = tasks.findById(id).orElseThrow(() -> new NotFoundException("Task not found."));
        if (!caller.orgWide() && !task.getAssigneeUserId().equals(caller.userId())) {
            throw new NotFoundException("Task not found.");
        }
        return task;
    }

    private void move(Task task, Caller caller, TaskStatus to, String event, String note) {
        TaskStatus before = task.getStatus();
        task.moveTo(to, clock.instant());
        recorder.change(task, event, caller.userId(), "status", before, to, note);
    }

    private static void requireAssignee(Task task, Caller caller) {
        if (!task.getAssigneeUserId().equals(caller.userId())) {
            throw new ForbiddenFieldException("Only the Manager the task is assigned to can do this.");
        }
    }

    private static void requireNotAssignee(Task task, Caller caller, String verb) {
        if (task.getAssigneeUserId().equals(caller.userId())) {
            throw new ForbiddenFieldException("You cannot " + verb + " your own task.");
        }
    }

    private static void requireOrgWide(Caller caller, String what) {
        if (!caller.orgWide()) {
            throw new ForbiddenFieldException("Only Admin or Director can " + what + ".");
        }
    }

    private static void requireStatus(Task task, String verb, TaskStatus required) {
        if (task.getStatus() != required) {
            throw new ConflictException("The task cannot be " + verb + " because it is " + label(task.getStatus()) + ".");
        }
    }

    private void requireActiveManager(UUID userId) {
        if (userId == null) {
            throw new InvalidInputException("Choose who the task is for.");
        }
        boolean manager = managers.managerByUserId(userId).filter(ManagerQueries.ManagerRef::active).isPresent();
        boolean active = users.findById(userId).filter(AppUser::isActive).isPresent();
        if (!manager || !active) {
            throw new InvalidInputException("A task can be given only to an active Manager.");
        }
    }

    private static int escalationDays(Integer days) {
        int value = days == null ? DEFAULT_ESCALATE_DAYS : days;
        if (value < 1 || value > 30) {
            throw new InvalidInputException("Escalate after must be between 1 and 30 days.");
        }
        return value;
    }

    private static String cleanTitle(String raw) {
        String title = raw == null ? "" : raw.trim().replaceAll("\\s+", " ");
        if (title.length() < 3 || title.length() > 120) {
            throw new InvalidInputException("The title must be 3 to 120 characters.");
        }
        return title;
    }

    private static String clean(String raw, int max, String what) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String text = raw.trim();
        if (text.length() > max) {
            throw new InvalidInputException(what + " must be at most " + max + " characters.");
        }
        return text;
    }

    private static String required(String raw, int min, int max, String what) {
        String text = raw == null ? "" : raw.trim();
        if (text.length() < min) {
            throw new InvalidInputException(what + " is required (at least " + min + " characters).");
        }
        if (text.length() > max) {
            throw new InvalidInputException(what + " must be at most " + max + " characters.");
        }
        return text;
    }

    static String label(TaskStatus status) {
        return status.name().toLowerCase().replace('_', ' ');
    }
}
