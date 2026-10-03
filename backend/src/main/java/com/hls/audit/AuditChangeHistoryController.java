package com.hls.audit;

import com.hls.audit.changehistory.ChangeHistoryEntry;
import com.hls.audit.changehistory.ChangeHistoryEntryRepository;
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
 * View/export Change History (contracts/audit-api.md), restricted to `ADMIN`/`SYSTEM` by
 * {@link PermissionGuard} — re-checked here independently of whatever the frontend would render
 * (Constitution Principle X).
 */
@RestController
@RequestMapping("/api/v1/audit/change-history")
public class AuditChangeHistoryController {

    private final ChangeHistoryEntryRepository repository;
    private final PermissionGuard permissionGuard;

    public AuditChangeHistoryController(ChangeHistoryEntryRepository repository, PermissionGuard permissionGuard) {
        this.repository = repository;
        this.permissionGuard = permissionGuard;
    }

    @GetMapping
    public ResponseEntity<AuditPageResponse<ChangeHistoryView>> list(
            @RequestParam(required = false) UUID actorUserId,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) String entityId,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @AuthenticationPrincipal Jwt jwt) {
        permissionGuard.require(rolesOf(jwt), PermissionModule.AUDIT_CHANGE_HISTORY, PermissionAction.VIEW);
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "occurredAt"));
        Page<ChangeHistoryEntry> result = repository.findAll(spec(actorUserId, entityType, entityId, from, to), pageable);
        return ResponseEntity.ok(AuditPageResponse.from(result.map(ChangeHistoryView::from)));
    }

    @GetMapping("/export")
    public void export(
            @RequestParam(required = false) UUID actorUserId,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) String entityId,
            @RequestParam Instant from,
            @RequestParam Instant to,
            @AuthenticationPrincipal Jwt jwt,
            HttpServletResponse response)
            throws IOException {
        permissionGuard.require(rolesOf(jwt), PermissionModule.AUDIT_CHANGE_HISTORY, PermissionAction.EXPORT);
        CsvStreamingExporter.stream(
                response,
                "change-history.csv",
                List.of("occurredAt", "actorUserId", "entityType", "entityId", "field", "beforeValue", "afterValue"),
                pageable -> repository.findAll(spec(actorUserId, entityType, entityId, from, to), pageable),
                entry -> List.of(
                        String.valueOf(entry.getOccurredAt()),
                        entry.getActorUserId().toString(),
                        entry.getEntityType(),
                        entry.getEntityId(),
                        entry.getField(),
                        entry.getBeforeValue() == null ? "" : entry.getBeforeValue(),
                        entry.getAfterValue() == null ? "" : entry.getAfterValue()));
    }

    private static Specification<ChangeHistoryEntry> spec(
            UUID actorUserId, String entityType, String entityId, Instant from, Instant to) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (actorUserId != null) {
                predicates.add(cb.equal(root.get("actorUserId"), actorUserId));
            }
            if (entityType != null) {
                predicates.add(cb.equal(root.get("entityType"), entityType));
            }
            if (entityId != null) {
                predicates.add(cb.equal(root.get("entityId"), entityId));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("occurredAt"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("occurredAt"), to));
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

    public record ChangeHistoryView(
            UUID id,
            Instant occurredAt,
            UUID actorUserId,
            String entityType,
            String entityId,
            String field,
            String beforeValue,
            String afterValue) {
        static ChangeHistoryView from(ChangeHistoryEntry entry) {
            return new ChangeHistoryView(
                    entry.getId(),
                    entry.getOccurredAt(),
                    entry.getActorUserId(),
                    entry.getEntityType(),
                    entry.getEntityId(),
                    entry.getField(),
                    entry.getBeforeValue(),
                    entry.getAfterValue());
        }
    }
}
