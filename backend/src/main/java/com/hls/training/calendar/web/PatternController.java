package com.hls.training.calendar.web;

import com.hls.identity.permissions.PermissionAction;
import com.hls.identity.permissions.PermissionGuard;
import com.hls.identity.permissions.PermissionModule;
import com.hls.identity.user.Role;
import com.hls.school.api.CallerContext;
import com.hls.training.calendar.internal.Caller;
import com.hls.training.calendar.internal.PatternService;
import com.hls.training.calendar.internal.PatternService.PatternBody;
import com.hls.training.calendar.internal.PatternService.PatternView;
import com.hls.training.calendar.internal.PatternService.PreviewRow;
import com.hls.training.calendar.internal.PatternService.SkipBody;
import com.hls.training.calendar.internal.PatternService.StopBody;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Standing monthly patterns (spec 024 contracts/training-api.md, Patterns). All calls need the {@code TRAINING} grant. */
@RestController("trainingPatternController")
@RequestMapping("/api/v1/training/patterns")
public class PatternController {

    private final PatternService service;
    private final PermissionGuard guard;

    public PatternController(PatternService service, PermissionGuard guard) {
        this.service = service;
        this.guard = guard;
    }

    @GetMapping
    public List<PatternView> list(@AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.VIEW);
        return service.list();
    }

    @PostMapping
    public ResponseEntity<PatternView> create(@RequestBody PatternBody body, @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.CREATE);
        return ResponseEntity.status(201).body(service.create(caller(jwt), body));
    }

    @PostMapping("/preview")
    public List<PreviewRow> preview(@RequestBody PatternBody body, @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.CREATE);
        return service.preview(body);
    }

    @PutMapping("/{id}")
    public PatternView edit(@PathVariable UUID id, @RequestBody PatternBody body, @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.EDIT);
        return service.edit(caller(jwt), id, body);
    }

    @PostMapping("/{id}/skips")
    public PatternView skip(@PathVariable UUID id, @RequestBody SkipBody body, @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.EDIT);
        return service.skip(caller(jwt), id, body);
    }

    @DeleteMapping("/{id}/skips/{month}")
    public PatternView unskip(
            @PathVariable UUID id,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate month,
            @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.EDIT);
        return service.unskip(caller(jwt), id, month);
    }

    @PostMapping("/{id}/stop")
    public PatternView stop(@PathVariable UUID id, @RequestBody StopBody body, @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.EDIT);
        return service.stop(caller(jwt), id, body);
    }

    private void require(Jwt jwt, PermissionAction action) {
        guard.require(CallerContext.roles(jwt), PermissionModule.TRAINING, action);
    }

    private static Caller caller(Jwt jwt) {
        Set<Role> roles = CallerContext.roles(jwt);
        return new Caller(CallerContext.userId(jwt), roles);
    }
}
