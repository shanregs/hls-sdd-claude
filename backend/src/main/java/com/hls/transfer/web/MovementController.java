package com.hls.transfer.web;

import com.hls.identity.permissions.PermissionAction;
import com.hls.identity.permissions.PermissionGuard;
import com.hls.identity.permissions.PermissionModule;
import com.hls.school.api.CallerContext;
import com.hls.school.api.PageResponse;
import com.hls.transfer.internal.Caller;
import com.hls.transfer.internal.MovementQueries;
import com.hls.transfer.internal.MovementQueries.Candidate;
import com.hls.transfer.internal.MovementQueries.Vacancy;
import com.hls.transfer.internal.MovementService;
import com.hls.transfer.internal.MovementService.ChangeBody;
import com.hls.transfer.internal.MovementService.CheckBody;
import com.hls.transfer.internal.MovementService.CheckResult;
import com.hls.transfer.internal.MovementService.DecisionBody;
import com.hls.transfer.internal.MovementService.ReplacementBody;
import com.hls.transfer.internal.MovementService.TransferBody;
import com.hls.transfer.internal.RequestView;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Transfer and replacement requests (contracts/transfer-api.md). Every endpoint re-checks grant and scope on the server. */
@RestController
@RequestMapping("/api/v1/transfers")
public class MovementController {

    private static final PermissionModule MODULE = PermissionModule.TEACHER_TRANSFERS;

    private final MovementService service;
    private final MovementQueries queries;
    private final PermissionGuard guard;

    public MovementController(MovementService service, MovementQueries queries, PermissionGuard guard) {
        this.service = service;
        this.queries = queries;
        this.guard = guard;
    }

    private void require(Jwt jwt, PermissionAction action) {
        guard.require(CallerContext.roles(jwt), MODULE, action);
    }

    private Caller caller(Jwt jwt) {
        var roles = CallerContext.roles(jwt);
        return new Caller(
                CallerContext.userId(jwt),
                roles,
                guard.isGranted(roles, MODULE, PermissionAction.EDIT),
                guard.isGranted(roles, MODULE, PermissionAction.APPROVE));
    }

    @GetMapping
    public PageResponse<RequestView> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID schoolId,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.VIEW);
        Set<String> statuses = status == null || status.isBlank()
                ? null
                : new LinkedHashSet<>(Arrays.stream(status.split(",")).map(String::trim).map(String::toUpperCase).toList());
        return queries.list(caller(jwt), statuses, schoolId, from, to, page, size);
    }

    @GetMapping("/{id}")
    public RequestView get(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.VIEW);
        return queries.view(caller(jwt), id);
    }

    @PostMapping("/transfers")
    @ResponseStatus(HttpStatus.CREATED)
    public RequestView raiseTransfer(@RequestBody TransferBody body, @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.CREATE);
        return service.raiseTransfer(caller(jwt), body);
    }

    @PostMapping("/replacements")
    @ResponseStatus(HttpStatus.CREATED)
    public RequestView raiseReplacement(@RequestBody ReplacementBody body, @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.CREATE);
        return service.raiseReplacement(caller(jwt), body);
    }

    @PostMapping("/check")
    public CheckResult check(@RequestBody CheckBody body, @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.CREATE);
        return service.check(caller(jwt), body);
    }

    @GetMapping("/candidates")
    public List<Candidate> candidates(@RequestParam UUID schoolId, @RequestParam LocalDate on, @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.CREATE);
        return queries.candidates(caller(jwt), schoolId, on);
    }

    @GetMapping("/vacancies")
    public List<Vacancy> vacancies(@RequestParam UUID schoolId, @RequestParam LocalDate on, @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.CREATE);
        return queries.vacancies(schoolId, on);
    }

    @PatchMapping("/{id}")
    public RequestView change(@PathVariable UUID id, @RequestBody ChangeBody body, @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.EDIT);
        return service.changePending(caller(jwt), id, body);
    }

    @PostMapping("/{id}/approve")
    public RequestView approve(@PathVariable UUID id, @RequestBody DecisionBody body, @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.APPROVE);
        return service.approve(caller(jwt), id, body);
    }

    @PostMapping("/{id}/reject")
    public RequestView reject(@PathVariable UUID id, @RequestBody DecisionBody body, @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.APPROVE);
        return service.reject(caller(jwt), id, body);
    }

    @PostMapping("/{id}/cancel")
    public RequestView cancel(@PathVariable UUID id, @RequestBody DecisionBody body, @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.EDIT);
        return service.cancel(caller(jwt), id, body);
    }
}
