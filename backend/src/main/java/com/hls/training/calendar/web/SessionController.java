package com.hls.training.calendar.web;

import com.hls.identity.permissions.PermissionAction;
import com.hls.identity.permissions.PermissionGuard;
import com.hls.identity.permissions.PermissionModule;
import com.hls.identity.user.Role;
import com.hls.school.api.CallerContext;
import com.hls.school.api.InvalidInputException;
import com.hls.training.calendar.internal.AssignmentService;
import com.hls.training.calendar.internal.AssignmentService.AssignBody;
import com.hls.training.calendar.internal.AssignmentService.AssignResult;
import com.hls.training.calendar.internal.AssignmentService.AssignmentRow;
import com.hls.training.calendar.internal.AssignmentService.NoteBody;
import com.hls.training.calendar.internal.AssignmentService.Preview;
import com.hls.training.calendar.internal.AssignmentService.RemoveBody;
import com.hls.training.calendar.internal.Caller;
import com.hls.training.calendar.internal.RegistrationService;
import com.hls.training.calendar.internal.RegistrationService.Result;
import com.hls.training.calendar.internal.RegistrationService.RosterRow;
import com.hls.training.calendar.internal.SessionQueries;
import com.hls.training.calendar.internal.SessionQueries.SessionView;
import com.hls.training.calendar.internal.SessionService;
import com.hls.training.calendar.internal.SessionService.CancelBody;
import com.hls.training.calendar.internal.SessionService.SessionBody;
import com.hls.training.calendar.internal.SessionService.VersionBody;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sessions and registrations of the Training Calendar (spec 024 contracts/training-api.md). The grants are re-checked here on
 * every call; the services decide what the grants allow.
 */
@RestController("trainingSessionController")
@RequestMapping("/api/v1/training")
public class SessionController {

    private final SessionService service;
    private final SessionQueries queries;
    private final RegistrationService registrations;
    private final AssignmentService assignmentService;
    private final PermissionGuard guard;

    public SessionController(
            SessionService service,
            SessionQueries queries,
            RegistrationService registrations,
            AssignmentService assignmentService,
            PermissionGuard guard) {
        this.assignmentService = assignmentService;
        this.service = service;
        this.queries = queries;
        this.registrations = registrations;
        this.guard = guard;
    }

    // ------------------------------------------------------------------ sessions

    @GetMapping("/sessions")
    public List<SessionView> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String trainer,
            @RequestParam(required = false) UUID personId,
            @AuthenticationPrincipal Jwt jwt) {
        return queries.list(viewer(jwt), from, to, type, status, trainer, personId);
    }

    @PostMapping("/sessions")
    public ResponseEntity<SessionView> create(@RequestBody SessionBody body, @AuthenticationPrincipal Jwt jwt) {
        requireTraining(jwt, PermissionAction.CREATE);
        Caller caller = caller(jwt);
        UUID id = service.create(caller, body);
        return ResponseEntity.status(201).body(queries.detail(caller, id));
    }

    @GetMapping("/sessions/{id}")
    public SessionView detail(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return queries.detail(viewer(jwt), id);
    }

    @PutMapping("/sessions/{id}")
    public SessionView edit(@PathVariable UUID id, @RequestBody SessionBody body, @AuthenticationPrincipal Jwt jwt) {
        requireTraining(jwt, PermissionAction.EDIT);
        Caller caller = caller(jwt);
        service.edit(caller, id, body);
        return queries.detail(caller, id);
    }

    @PostMapping("/sessions/{id}/publish")
    public SessionView publish(@PathVariable UUID id, @RequestBody(required = false) VersionBody body, @AuthenticationPrincipal Jwt jwt) {
        requireTraining(jwt, PermissionAction.EDIT);
        Caller caller = caller(jwt);
        service.publish(caller, id, body == null ? new VersionBody(null) : body);
        return queries.detail(caller, id);
    }

    @PostMapping("/sessions/{id}/cancel")
    public SessionView cancel(@PathVariable UUID id, @RequestBody CancelBody body, @AuthenticationPrincipal Jwt jwt) {
        requireTraining(jwt, PermissionAction.EDIT);
        Caller caller = caller(jwt);
        service.cancel(caller, id, body);
        return queries.detail(caller, id);
    }

    @DeleteMapping("/sessions/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        requireTraining(jwt, PermissionAction.DELETE);
        service.delete(caller(jwt), id);
        return ResponseEntity.noContent().build();
    }

    // ------------------------------------------------------------------ registrations

    /** A Teacher registers themselves (empty body); Admin or Director register a Teacher with teacherId. */
    @PostMapping("/sessions/{id}/registrations")
    public ResponseEntity<Result> register(
            @PathVariable UUID id, @RequestBody(required = false) Map<String, Object> body, @AuthenticationPrincipal Jwt jwt) {
        UUID teacherId = teacherIdOf(body);
        if (teacherId != null) {
            requireTraining(jwt, PermissionAction.CREATE);
        } else {
            requireRegistration(jwt, PermissionAction.CREATE);
        }
        Result result = registrations.register(caller(jwt), id, teacherId);
        return ResponseEntity.status(result.created() ? 201 : 200).body(result);
    }

    @DeleteMapping("/sessions/{id}/registrations/me")
    public Result cancelOwn(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        requireRegistration(jwt, PermissionAction.EDIT);
        return registrations.cancel(caller(jwt), id, null);
    }

    @DeleteMapping("/sessions/{id}/registrations/{teacherId}")
    public Result cancelFor(@PathVariable UUID id, @PathVariable UUID teacherId, @AuthenticationPrincipal Jwt jwt) {
        requireTraining(jwt, PermissionAction.EDIT);
        return registrations.cancel(caller(jwt), id, teacherId);
    }

    @GetMapping("/sessions/{id}/registrations")
    public List<RosterRow> roster(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        requireTraining(jwt, PermissionAction.VIEW);
        return registrations.roster(caller(jwt), id);
    }

    // ------------------------------------------------------------------ assignments

    @PostMapping("/sessions/{id}/assignments/preview")
    public Preview previewAssignment(@PathVariable UUID id, @RequestBody AssignBody body, @AuthenticationPrincipal Jwt jwt) {
        requireTraining(jwt, PermissionAction.EDIT);
        queries.requireVisible(caller(jwt), id);
        return assignmentService.preview(id, body);
    }

    @PostMapping("/sessions/{id}/assignments")
    public ResponseEntity<AssignResult> assign(@PathVariable UUID id, @RequestBody AssignBody body, @AuthenticationPrincipal Jwt jwt) {
        requireTraining(jwt, PermissionAction.EDIT);
        queries.requireVisible(caller(jwt), id);
        return ResponseEntity.status(201).body(assignmentService.assign(caller(jwt), id, body));
    }

    @DeleteMapping("/sessions/{id}/assignments/{assignmentId}")
    public ResponseEntity<Void> removeAssignment(
            @PathVariable UUID id,
            @PathVariable UUID assignmentId,
            @RequestBody(required = false) RemoveBody body,
            @AuthenticationPrincipal Jwt jwt) {
        requireTraining(jwt, PermissionAction.EDIT);
        assignmentService.remove(caller(jwt), id, assignmentId, body);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/sessions/{id}/assignments")
    public List<AssignmentRow> assignmentRoster(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        requireTraining(jwt, PermissionAction.VIEW);
        return assignmentService.roster(caller(jwt), id);
    }

    /** A Teacher with TRAINING_REGISTRATION Create, or a Manager with TRAINING View, says they cannot attend. */
    @PostMapping("/sessions/{id}/assignments/me/cannot-attend")
    public ResponseEntity<Void> cannotAttend(@PathVariable UUID id, @RequestBody NoteBody body, @AuthenticationPrincipal Jwt jwt) {
        Set<Role> roles = CallerContext.roles(jwt);
        boolean allowed = can(roles, PermissionModule.TRAINING_REGISTRATION, PermissionAction.CREATE)
                || (roles.contains(Role.MANAGER) && can(roles, PermissionModule.TRAINING, PermissionAction.VIEW));
        if (!allowed) {
            throw new AccessDeniedException("Not authorized for training");
        }
        assignmentService.cannotAttend(caller(jwt), id, body);
        return ResponseEntity.noContent().build();
    }

    // ------------------------------------------------------------------ helpers

    private static UUID teacherIdOf(Map<String, Object> body) {
        if (body == null || body.get("teacherId") == null) {
            return null;
        }
        try {
            return UUID.fromString(body.get("teacherId").toString());
        } catch (IllegalArgumentException e) {
            throw new InvalidInputException("The Teacher is not valid.");
        }
    }

    private void requireTraining(Jwt jwt, PermissionAction action) {
        guard.require(CallerContext.roles(jwt), PermissionModule.TRAINING, action);
    }

    private void requireRegistration(Jwt jwt, PermissionAction action) {
        guard.require(CallerContext.roles(jwt), PermissionModule.TRAINING_REGISTRATION, action);
    }

    /** Reads are open to a holder of TRAINING View (staff) or TRAINING_REGISTRATION View (Teacher). */
    private Caller viewer(Jwt jwt) {
        Set<Role> roles = CallerContext.roles(jwt);
        if (!can(roles, PermissionModule.TRAINING, PermissionAction.VIEW)
                && !can(roles, PermissionModule.TRAINING_REGISTRATION, PermissionAction.VIEW)) {
            throw new AccessDeniedException("Not authorized for training");
        }
        return caller(jwt);
    }

    private Caller caller(Jwt jwt) {
        Set<Role> roles = CallerContext.roles(jwt);
        return new Caller(CallerContext.userId(jwt), roles, grants(roles));
    }

    private Caller.Grants grants(Set<Role> roles) {
        return new Caller.Grants(
                can(roles, PermissionModule.TRAINING, PermissionAction.VIEW),
                can(roles, PermissionModule.TRAINING, PermissionAction.CREATE),
                can(roles, PermissionModule.TRAINING, PermissionAction.EDIT),
                can(roles, PermissionModule.TRAINING, PermissionAction.DELETE),
                can(roles, PermissionModule.TRAINING, PermissionAction.EXPORT),
                can(roles, PermissionModule.TRAINING_ATTENDANCE, PermissionAction.VIEW),
                can(roles, PermissionModule.TRAINING_ATTENDANCE, PermissionAction.EDIT),
                can(roles, PermissionModule.TRAINING_REGISTRATION, PermissionAction.VIEW),
                can(roles, PermissionModule.TRAINING_REGISTRATION, PermissionAction.CREATE),
                can(roles, PermissionModule.TRAINING_REGISTRATION, PermissionAction.EDIT));
    }

    private boolean can(Set<Role> roles, PermissionModule module, PermissionAction action) {
        return guard.isGranted(roles, module, action);
    }
}
