package com.hls.audit;

import com.hls.audit.logs.AuditLogEntryView;
import com.hls.audit.logs.AuditLogQueryService;
import com.hls.audit.support.AuditPageResponse;
import com.hls.audit.support.CsvStreamingExporter;
import com.hls.identity.permissions.PermissionAction;
import com.hls.identity.permissions.PermissionGuard;
import com.hls.identity.permissions.PermissionModule;
import com.hls.identity.user.Role;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * View/export the unified Audit Logs feed (contracts/audit-api.md, FR-008), restricted to
 * `ADMIN`/`SYSTEM` by {@link PermissionGuard}.
 */
@RestController
@RequestMapping("/api/v1/audit/logs")
public class AuditLogsController {

    private final AuditLogQueryService auditLogQueryService;
    private final PermissionGuard permissionGuard;

    public AuditLogsController(AuditLogQueryService auditLogQueryService, PermissionGuard permissionGuard) {
        this.auditLogQueryService = auditLogQueryService;
        this.permissionGuard = permissionGuard;
    }

    @GetMapping
    public ResponseEntity<AuditPageResponse<AuditLogEntryView>> list(
            @RequestParam(required = false) List<String> type,
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @AuthenticationPrincipal Jwt jwt) {
        permissionGuard.require(rolesOf(jwt), PermissionModule.AUDIT_LOGS, PermissionAction.VIEW);
        Set<String> types = (type == null || type.isEmpty()) ? AuditLogQueryService.ALL_TYPES : Set.copyOf(type);
        Page<AuditLogEntryView> result = auditLogQueryService.query(types, userId, from, to, PageRequest.of(page, size));
        return ResponseEntity.ok(AuditPageResponse.from(result));
    }

    @GetMapping("/export")
    public void export(
            @RequestParam(required = false) List<String> type,
            @RequestParam(required = false) UUID userId,
            @RequestParam Instant from,
            @RequestParam Instant to,
            @AuthenticationPrincipal Jwt jwt,
            HttpServletResponse response)
            throws IOException {
        permissionGuard.require(rolesOf(jwt), PermissionModule.AUDIT_LOGS, PermissionAction.EXPORT);
        Set<String> types = (type == null || type.isEmpty()) ? AuditLogQueryService.ALL_TYPES : Set.copyOf(type);
        CsvStreamingExporter.stream(
                response,
                "audit-logs.csv",
                List.of("occurredAt", "type", "actorUserId", "summary"),
                pageable -> auditLogQueryService.query(types, userId, from, to, pageable),
                entry -> List.of(
                        String.valueOf(entry.occurredAt()),
                        entry.type(),
                        entry.actorUserId() == null ? "" : entry.actorUserId().toString(),
                        entry.summary()));
    }

    private static Set<Role> rolesOf(Jwt jwt) {
        Set<Role> roles = EnumSet.noneOf(Role.class);
        for (String role : jwt.getClaimAsStringList("roles")) {
            roles.add(Role.valueOf(role));
        }
        return roles;
    }
}
