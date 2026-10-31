package com.hls.tasks.web;

import com.hls.files.api.FileStore.FileContent;
import com.hls.identity.permissions.PermissionAction;
import com.hls.identity.permissions.PermissionGuard;
import com.hls.identity.permissions.PermissionModule;
import com.hls.school.api.CallerContext;
import com.hls.school.api.InvalidInputException;
import com.hls.school.api.PageResponse;
import com.hls.tasks.api.TaskType;
import com.hls.tasks.internal.TaskQueries;
import com.hls.tasks.internal.TaskService;
import com.hls.tasks.internal.TaskService.Caller;
import com.hls.tasks.internal.TaskService.CreateRequest;
import com.hls.tasks.internal.TaskService.UpdateRequest;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Manager tasks (contracts/tasks-api.md), gated by the TASKS grants and the task's own rules. */
@RestController
@RequestMapping("/api/v1/tasks")
public class TaskController {

    private final TaskService service;
    private final TaskQueries queries;
    private final PermissionGuard guard;

    public TaskController(TaskService service, TaskQueries queries, PermissionGuard guard) {
        this.service = service;
        this.queries = queries;
        this.guard = guard;
    }

    public record CreateBody(
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

    public record UpdateBody(
            String title, String description, UUID assigneeUserId, LocalDate dueOn, Integer escalateAfterDays, String place, Long version) {}

    public record NoteBody(String note) {}

    /** Spec 040: a Manager's own work logged for today; no assignee and no date (the server decides both). */
    public record SameDayBody(
            TaskType type,
            String title,
            String description,
            String linkedType,
            UUID linkedId,
            String linkedLabel,
            String place,
            String status,
            String completionNote,
            java.time.LocalDate capturedOn) {}

    public record ReasonBody(String reason) {}

    public record OverdueBody(String reason, LocalDate followUpOn) {}

    private void require(Jwt jwt, PermissionAction action) {
        guard.require(CallerContext.roles(jwt), PermissionModule.TASKS, action);
    }

    private static Caller caller(Jwt jwt) {
        return new Caller(CallerContext.userId(jwt), CallerContext.roles(jwt));
    }

    @GetMapping
    public PageResponse<TaskQueries.Row> list(
            @RequestParam(required = false) String view,
            @RequestParam(required = false) UUID assigneeUserId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Boolean sameDay,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.VIEW);
        return queries.list(caller(jwt), view, assigneeUserId, type, status, sameDay, page, size);
    }

    @GetMapping("/summary")
    public TaskQueries.Summary summary(@RequestParam(required = false) UUID assigneeUserId, @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.VIEW);
        return queries.summary(caller(jwt), assigneeUserId);
    }

    @GetMapping("/types")
    public List<TaskQueries.TypeOption> types(@AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.VIEW);
        return queries.types();
    }

    @GetMapping("/assignees")
    public List<TaskQueries.Assignee> assignees(@AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.CREATE);
        return queries.assignees(caller(jwt));
    }

    @PostMapping
    public ResponseEntity<TaskQueries.Detail> create(@RequestBody CreateBody body, @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.CREATE);
        Caller caller = caller(jwt);
        UUID id = service.create(
                caller,
                new CreateRequest(
                        body.type(),
                        body.title(),
                        body.description(),
                        body.assigneeUserId(),
                        body.dueOn(),
                        body.escalateAfterDays(),
                        body.linkedType(),
                        body.linkedId(),
                        body.linkedLabel(),
                        body.place()));
        return ResponseEntity.status(201).body(queries.detail(caller, id));
    }

    /** Spec 040: log own work for today, saved as In progress or final in one step. */
    @PostMapping("/same-day")
    public ResponseEntity<TaskService.SameDayResult> logSameDay(
            @RequestBody SameDayBody body,
            @RequestHeader(value = "Idempotency-Key", required = false) UUID key,
            @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.CREATE);
        TaskService.SameDayResult result = service.logSameDay(
                caller(jwt),
                new TaskService.SameDayRequest(
                        body.type(),
                        body.title(),
                        body.description(),
                        body.linkedType(),
                        body.linkedId(),
                        body.linkedLabel(),
                        body.place(),
                        body.status(),
                        body.completionNote(),
                        key,
                        body.capturedOn()));
        return ResponseEntity.status(201).body(result);
    }

    /** Spec 040: a task planned for today is marked done on the spot; final at once. */
    @PostMapping("/{id}/done-on-the-spot")
    public TaskQueries.Detail doneOnTheSpot(
            @PathVariable UUID id, @RequestBody NoteBody body, @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.EDIT);
        Caller caller = caller(jwt);
        service.markDoneOnTheSpot(caller, id, body == null ? null : body.note());
        return queries.detail(caller, id);
    }

    @GetMapping("/{id}")
    public TaskQueries.Detail get(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.VIEW);
        return queries.detail(caller(jwt), id);
    }

    @PutMapping("/{id}")
    public TaskQueries.Detail update(@PathVariable UUID id, @RequestBody UpdateBody body, @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.EDIT);
        Caller caller = caller(jwt);
        service.update(
                caller,
                id,
                new UpdateRequest(
                        body.title(), body.description(), body.assigneeUserId(), body.dueOn(), body.escalateAfterDays(), body.place(), body.version()));
        return queries.detail(caller, id);
    }

    @PostMapping("/{id}/acknowledge")
    public TaskQueries.Detail acknowledge(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.EDIT);
        Caller caller = caller(jwt);
        service.acknowledge(caller, id);
        return queries.detail(caller, id);
    }

    @PostMapping("/{id}/start")
    public TaskQueries.Detail start(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.EDIT);
        Caller caller = caller(jwt);
        service.start(caller, id);
        return queries.detail(caller, id);
    }

    @PostMapping("/{id}/overdue-reason")
    public TaskQueries.Detail overdueReason(@PathVariable UUID id, @RequestBody OverdueBody body, @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.EDIT);
        Caller caller = caller(jwt);
        service.overdueReason(caller, id, body == null ? null : body.reason(), body == null ? null : body.followUpOn());
        return queries.detail(caller, id);
    }

    @PostMapping("/{id}/complete")
    public TaskQueries.Detail complete(@PathVariable UUID id, @RequestBody NoteBody body, @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.EDIT);
        Caller caller = caller(jwt);
        service.complete(caller, id, body == null ? null : body.note());
        return queries.detail(caller, id);
    }

    @PostMapping("/{id}/evidence")
    public ResponseEntity<TaskQueries.Detail> evidence(
            @PathVariable UUID id, @RequestParam("file") MultipartFile file, @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.EDIT);
        Caller caller = caller(jwt);
        try {
            service.addEvidence(caller, id, file.getOriginalFilename(), file.getBytes());
        } catch (IOException e) {
            throw new InvalidInputException("The file could not be read.");
        }
        return ResponseEntity.status(201).body(queries.detail(caller, id));
    }

    @PostMapping("/{id}/verify")
    public TaskQueries.Detail verify(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.APPROVE);
        Caller caller = caller(jwt);
        service.verify(caller, id);
        return queries.detail(caller, id);
    }

    @PostMapping("/{id}/send-back")
    public TaskQueries.Detail sendBack(@PathVariable UUID id, @RequestBody ReasonBody body, @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.APPROVE);
        Caller caller = caller(jwt);
        service.sendBack(caller, id, body == null ? null : body.reason());
        return queries.detail(caller, id);
    }

    @PostMapping("/{id}/cancel")
    public TaskQueries.Detail cancel(@PathVariable UUID id, @RequestBody ReasonBody body, @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.EDIT);
        Caller caller = caller(jwt);
        service.cancel(caller, id, body == null ? null : body.reason());
        return queries.detail(caller, id);
    }

    /** Always sent as an attachment with {@code nosniff}, never shown inline. */
    @GetMapping("/files/{fileId}")
    public ResponseEntity<byte[]> download(@PathVariable UUID fileId, @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.VIEW);
        FileContent content = service.download(caller(jwt), fileId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(content.file().contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(content.file().name()).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(CacheControl.noStore())
                .body(content.bytes());
    }
}
