package com.hls.audit;

import com.hls.audit.support.AuditPageResponse;
import com.hls.audit.support.CsvStreamingExporter;
import com.hls.audit.support.LocationView;
import com.hls.audit.support.SourceFilter;
import com.hls.audit.useractivity.UserActivityEntry;
import com.hls.audit.useractivity.UserActivityEntryRepository;
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
 * View/export User Activity (contracts/audit-api.md), restricted to `ADMIN`/`SYSTEM` by
 * {@link PermissionGuard} — re-checked here independently of whatever the frontend would render
 * (Constitution Principle X).
 */
@RestController
@RequestMapping("/api/v1/audit/user-activity")
public class AuditUserActivityController {

    private final UserActivityEntryRepository repository;
    private final PermissionGuard permissionGuard;

    public AuditUserActivityController(UserActivityEntryRepository repository, PermissionGuard permissionGuard) {
        this.repository = repository;
        this.permissionGuard = permissionGuard;
    }

    @GetMapping
    public ResponseEntity<AuditPageResponse<UserActivityView>> list(
            @RequestParam(required = false) UUID actorUserId,
            @RequestParam(required = false) UUID affectedUserId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) String source,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @AuthenticationPrincipal Jwt jwt) {
        permissionGuard.require(rolesOf(jwt), PermissionModule.AUDIT_USER_ACTIVITY, PermissionAction.VIEW);
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "occurredAt"));
        Page<UserActivityEntry> result =
                repository.findAll(spec(actorUserId, affectedUserId, action, from, to, SourceFilter.parse(source)), pageable);
        return ResponseEntity.ok(AuditPageResponse.from(result.map(UserActivityView::from)));
    }

    @GetMapping("/export")
    public void export(
            @RequestParam(required = false) UUID actorUserId,
            @RequestParam(required = false) UUID affectedUserId,
            @RequestParam(required = false) String action,
            @RequestParam Instant from,
            @RequestParam Instant to,
            @RequestParam(required = false) String source,
            @AuthenticationPrincipal Jwt jwt,
            HttpServletResponse response)
            throws IOException {
        permissionGuard.require(rolesOf(jwt), PermissionModule.AUDIT_USER_ACTIVITY, PermissionAction.EXPORT);
        String sourceFilter = SourceFilter.parse(source);
        CsvStreamingExporter.stream(
                response,
                "user-activity.csv",
                List.of(
                        "occurredAt", "actorUserId", "affectedUserId", "action", "detail",
                        "source", "appVersion", "locationStatus", "latitude", "longitude", "accuracyMeters"),
                pageable -> repository.findAll(spec(actorUserId, affectedUserId, action, from, to, sourceFilter), pageable),
                entry -> List.of(
                        String.valueOf(entry.getOccurredAt()),
                        entry.getActorUserId() == null ? "" : entry.getActorUserId().toString(),
                        entry.getAffectedUserId().toString(),
                        entry.getAction(),
                        entry.getDetail() == null ? "" : entry.getDetail(),
                        entry.getOrigin().getSource(),
                        entry.getOrigin().getAppVersion() == null ? "" : entry.getOrigin().getAppVersion(),
                        entry.getOrigin().getLocationStatus(),
                        entry.getOrigin().getLatitude() == null ? "" : entry.getOrigin().getLatitude().toPlainString(),
                        entry.getOrigin().getLongitude() == null ? "" : entry.getOrigin().getLongitude().toPlainString(),
                        entry.getOrigin().getAccuracyMeters() == null ? "" : entry.getOrigin().getAccuracyMeters().toString()));
    }

    private static Specification<UserActivityEntry> spec(
            UUID actorUserId, UUID affectedUserId, String action, Instant from, Instant to, String source) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (actorUserId != null) {
                predicates.add(cb.equal(root.get("actorUserId"), actorUserId));
            }
            if (affectedUserId != null) {
                predicates.add(cb.equal(root.get("affectedUserId"), affectedUserId));
            }
            if (action != null) {
                predicates.add(cb.equal(root.get("action"), action));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("occurredAt"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("occurredAt"), to));
            }
            if (source != null) {
                predicates.add(cb.equal(root.get("origin").get("source"), source));
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

    public record UserActivityView(
            UUID id,
            Instant occurredAt,
            UUID actorUserId,
            UUID affectedUserId,
            String action,
            String detail,
            String source,
            String appVersion,
            LocationView location) {
        static UserActivityView from(UserActivityEntry entry) {
            return new UserActivityView(
                    entry.getId(),
                    entry.getOccurredAt(),
                    entry.getActorUserId(),
                    entry.getAffectedUserId(),
                    entry.getAction(),
                    entry.getDetail(),
                    entry.getOrigin().getSource(),
                    entry.getOrigin().getAppVersion(),
                    entry.getOrigin().location());
        }
    }
}
