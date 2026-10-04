package com.hls.audit;

import com.hls.audit.apiaccess.ApiAccessEntry;
import com.hls.audit.apiaccess.ApiAccessEntryRepository;
import com.hls.audit.support.AuditPageResponse;
import com.hls.audit.support.CsvStreamingExporter;
import com.hls.audit.support.LocationView;
import com.hls.identity.clientcontext.LocationStatus;
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
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * View/export the API Access trail (spec 018 FR-023a, FR-028, contracts/mobile-api.md): one entry per
 * request made by the Android app, with where it came from. Restricted by the {@code AUDIT_API_ACCESS}
 * permission (Admin and System by default), re-checked here independently of whatever the frontend
 * would render (Constitution Principle X).
 */
@RestController
@RequestMapping("/api/v1/audit/api-access")
public class AuditApiAccessController {

    private final ApiAccessEntryRepository repository;
    private final PermissionGuard permissionGuard;

    public AuditApiAccessController(ApiAccessEntryRepository repository, PermissionGuard permissionGuard) {
        this.repository = repository;
        this.permissionGuard = permissionGuard;
    }

    @GetMapping
    public ResponseEntity<AuditPageResponse<ApiAccessView>> list(
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) String locationStatus,
            @RequestParam(required = false) String httpMethod,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @AuthenticationPrincipal Jwt jwt) {
        permissionGuard.require(rolesOf(jwt), PermissionModule.AUDIT_API_ACCESS, PermissionAction.VIEW);
        Specification<ApiAccessEntry> filter = spec(userId, from, to, validStatus(locationStatus), httpMethod);
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "occurredAt"));
        Page<ApiAccessEntry> result = repository.findAll(filter, pageable);
        return ResponseEntity.ok(AuditPageResponse.from(result.map(ApiAccessView::from)));
    }

    @GetMapping("/export")
    public void export(
            @RequestParam(required = false) UUID userId,
            @RequestParam Instant from,
            @RequestParam Instant to,
            @RequestParam(required = false) String locationStatus,
            @RequestParam(required = false) String httpMethod,
            @AuthenticationPrincipal Jwt jwt,
            HttpServletResponse response)
            throws IOException {
        permissionGuard.require(rolesOf(jwt), PermissionModule.AUDIT_API_ACCESS, PermissionAction.EXPORT);
        Specification<ApiAccessEntry> filter = spec(userId, from, to, validStatus(locationStatus), httpMethod);
        CsvStreamingExporter.stream(
                response,
                "api-access.csv",
                List.of(
                        "occurredAt", "userId", "sessionId", "httpMethod", "routeTemplate", "statusCode", "source",
                        "appVersion", "locationStatus", "latitude", "longitude", "accuracyMeters", "locationCapturedAt"),
                pageable -> repository.findAll(filter, pageable),
                entry -> List.of(
                        String.valueOf(entry.getOccurredAt()),
                        entry.getUserId() == null ? "" : entry.getUserId().toString(),
                        entry.getSessionId() == null ? "" : entry.getSessionId().toString(),
                        entry.getHttpMethod(),
                        entry.getRouteTemplate(),
                        String.valueOf(entry.getStatusCode()),
                        entry.getOrigin().getSource(),
                        entry.getOrigin().getAppVersion() == null ? "" : entry.getOrigin().getAppVersion(),
                        entry.getOrigin().getLocationStatus(),
                        entry.getOrigin().getLatitude() == null
                                ? ""
                                : entry.getOrigin().getLatitude().toPlainString(),
                        entry.getOrigin().getLongitude() == null
                                ? ""
                                : entry.getOrigin().getLongitude().toPlainString(),
                        entry.getOrigin().getAccuracyMeters() == null
                                ? ""
                                : entry.getOrigin().getAccuracyMeters().toString(),
                        entry.getOrigin().getLocationCapturedAt() == null
                                ? ""
                                : entry.getOrigin().getLocationCapturedAt().toString()));
    }

    private static String validStatus(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocationStatus.valueOf(value.trim().toUpperCase(Locale.ROOT)).name();
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "unknown locationStatus: " + value);
        }
    }

    private static Specification<ApiAccessEntry> spec(
            UUID userId, Instant from, Instant to, String locationStatus, String httpMethod) {
        if (from != null && to != null && to.isBefore(from)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "to must not be before from");
        }
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
            if (locationStatus != null) {
                predicates.add(cb.equal(root.get("origin").get("locationStatus"), locationStatus));
            }
            if (httpMethod != null && !httpMethod.isBlank()) {
                predicates.add(cb.equal(root.get("httpMethod"), httpMethod.trim().toUpperCase(Locale.ROOT)));
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

    public record ApiAccessView(
            UUID id,
            Instant occurredAt,
            UUID userId,
            UUID sessionId,
            String httpMethod,
            String routeTemplate,
            int statusCode,
            String source,
            String appVersion,
            LocationView location) {
        static ApiAccessView from(ApiAccessEntry entry) {
            return new ApiAccessView(
                    entry.getId(),
                    entry.getOccurredAt(),
                    entry.getUserId(),
                    entry.getSessionId(),
                    entry.getHttpMethod(),
                    entry.getRouteTemplate(),
                    entry.getStatusCode(),
                    entry.getOrigin().getSource(),
                    entry.getOrigin().getAppVersion(),
                    entry.getOrigin().location());
        }
    }
}
