package com.hls.audit;

import com.hls.audit.loginhistory.LoginHistoryEntry;
import com.hls.audit.loginhistory.LoginHistoryEntryRepository;
import com.hls.audit.support.AuditPageResponse;
import com.hls.audit.support.CsvStreamingExporter;
import com.hls.identity.permissions.PermissionAction;
import com.hls.identity.permissions.PermissionGuard;
import com.hls.identity.permissions.PermissionModule;
import com.hls.identity.user.Role;
import jakarta.persistence.criteria.Predicate;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * View/export Login History (contracts/audit-api.md), restricted to `ADMIN`/`SYSTEM` by
 * {@link PermissionGuard} — re-checked here independently of whatever the frontend would render
 * (Constitution Principle X).
 */
@RestController
@RequestMapping("/api/v1/audit/login-history")
public class AuditLoginHistoryController {

    private final LoginHistoryEntryRepository repository;
    private final PermissionGuard permissionGuard;

    public AuditLoginHistoryController(LoginHistoryEntryRepository repository, PermissionGuard permissionGuard) {
        this.repository = repository;
        this.permissionGuard = permissionGuard;
    }

    @GetMapping
    public ResponseEntity<AuditPageResponse<LoginHistoryView>> list(
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) String method,
            @RequestParam(required = false) String outcome,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @AuthenticationPrincipal Jwt jwt) {
        permissionGuard.require(rolesOf(jwt), PermissionModule.AUDIT_LOGIN_HISTORY, PermissionAction.VIEW);
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "occurredAt"));
        Page<LoginHistoryEntry> result = repository.findAll(spec(userId, from, to, method, outcome), pageable);
        return ResponseEntity.ok(AuditPageResponse.from(result.map(LoginHistoryView::from)));
    }

    @GetMapping("/export")
    public void export(
            @RequestParam(required = false) UUID userId,
            @RequestParam Instant from,
            @RequestParam Instant to,
            @RequestParam(required = false) String method,
            @RequestParam(required = false) String outcome,
            @AuthenticationPrincipal Jwt jwt,
            HttpServletResponse response)
            throws IOException {
        permissionGuard.require(rolesOf(jwt), PermissionModule.AUDIT_LOGIN_HISTORY, PermissionAction.EXPORT);
        CsvStreamingExporter.stream(
                response,
                "login-history.csv",
                List.of("occurredAt", "userId", "phoneMasked", "method", "eventType", "outcome"),
                pageable -> repository.findAll(spec(userId, from, to, method, outcome), pageable),
                entry -> List.of(
                        String.valueOf(entry.getOccurredAt()),
                        entry.getUserId() == null ? "" : entry.getUserId().toString(),
                        entry.getPhoneMasked(),
                        entry.getMethod(),
                        entry.getEventType(),
                        entry.getOutcome()));
    }

    private static Specification<LoginHistoryEntry> spec(
            UUID userId, Instant from, Instant to, String method, String outcome) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (userId != null) {
                predicates.add(cb.equal(root.get("userId"), userId));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("occurredAt"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("occurredAt"), to));
            }
            if (method != null) {
                predicates.add(cb.equal(root.get("method"), method));
            }
            if (outcome != null) {
                predicates.add(cb.equal(root.get("outcome"), outcome));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private static Set<Role> rolesOf(Jwt jwt) {
        Set<Role> roles = EnumSet.noneOf(Role.class);
        for (String role : jwt.getClaimAsStringList("roles")) {
            roles.add(Role.valueOf(role));
        }
        return roles;
    }

    public record LoginHistoryView(
            UUID id,
            Instant occurredAt,
            UUID userId,
            String phoneMasked,
            String method,
            String eventType,
            String outcome) {
        static LoginHistoryView from(LoginHistoryEntry entry) {
            return new LoginHistoryView(
                    entry.getId(),
                    entry.getOccurredAt(),
                    entry.getUserId(),
                    entry.getPhoneMasked(),
                    entry.getMethod(),
                    entry.getEventType(),
                    entry.getOutcome());
        }
    }
}
