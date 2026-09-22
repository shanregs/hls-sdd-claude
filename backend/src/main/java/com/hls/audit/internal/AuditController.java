package com.hls.audit.internal;

import com.hls.audit.api.dto.AuditEntryView;
import java.util.List;
import java.util.Set;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoint per contracts/audit-api.yaml. Reads {@code @AuthenticationPrincipal
 * Jwt} directly, the same pattern {@code identity.internal.AuthController} and
 * {@code organization.internal.OrganizationController} use — no
 * {@code CurrentUserResolver}. GET-only by design (research.md §4/§7): there is
 * no write endpoint at all, so no HTTP path exists for anyone, including an
 * Admin, to create, edit, or delete an entry (FR-005).
 */
@RestController
@RequestMapping("/api/v1/audit")
public class AuditController {

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping("/{entityType}/{entityId}/history")
    public List<AuditEntryView> getHistory(
            @PathVariable String entityType, @PathVariable String entityId, @AuthenticationPrincipal Jwt jwt) {
        requireDirectorOrAdmin(jwt);
        return auditService.history(entityType, entityId);
    }

    // ---- helpers -------------------------------------------------------------------------

    /** FR-007/FR-008: Director-or-Admin check, same pattern as OrganizationController (research.md §6). */
    private void requireDirectorOrAdmin(Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList("roles");
        Set<String> roleSet = roles == null ? Set.of() : Set.copyOf(roles);
        if (!roleSet.contains("DIRECTOR") && !roleSet.contains("ADMIN")) {
            throw new AccessDeniedException("Only Director or Admin may view audit history");
        }
    }
}
