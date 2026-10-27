package com.hls.incentive.web;

import com.hls.identity.permissions.PermissionAction;
import com.hls.identity.permissions.PermissionGuard;
import com.hls.identity.permissions.PermissionModule;
import com.hls.incentive.internal.IncentiveDecisionService;
import com.hls.incentive.internal.IncentiveAccess;
import com.hls.incentive.internal.IncentiveAccess.Caller;
import com.hls.incentive.internal.IncentiveCsv;
import com.hls.incentive.internal.IncentiveDtos.DecisionRequest;
import com.hls.incentive.internal.IncentiveDtos.Detail;
import com.hls.incentive.internal.IncentiveDtos.NoEarnerRow;
import com.hls.incentive.internal.IncentiveDtos.Row;
import com.hls.incentive.internal.IncentiveDtos.Summary;
import com.hls.incentive.internal.IncentiveQueries;
import com.hls.incentive.internal.IncentiveQueries.Filter;
import com.hls.incentive.internal.IncentiveStatus;
import com.hls.school.api.CallerContext;
import com.hls.school.api.InvalidInputException;
import com.hls.school.api.PageResponse;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The Manager incentive screens and decisions (spec 030, contracts/incentive-api.md). Every answer follows the caller's scope. */
@RestController
@RequestMapping("/api/v1/incentives")
public class IncentiveController {

    private final IncentiveQueries queries;
    private final IncentiveDecisionService decisions;
    private final IncentiveCsv csv;
    private final PermissionGuard guard;
    private final IncentiveAccess access;

    public IncentiveController(
            IncentiveQueries queries, IncentiveDecisionService decisions, IncentiveCsv csv, PermissionGuard guard, IncentiveAccess access) {
        this.queries = queries;
        this.decisions = decisions;
        this.csv = csv;
        this.guard = guard;
        this.access = access;
    }

    @GetMapping
    public PageResponse<Row> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID managerId,
            @RequestParam(required = false) UUID zoneId,
            @RequestParam(required = false) String month,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "desc") String dir,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.VIEW);
        return queries.list(caller(jwt), filter(status, managerId, zoneId, month, q, sort, dir), page, size);
    }

    @GetMapping("/mine")
    public PageResponse<Row> mine(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.VIEW);
        return queries.mine(caller(jwt), filter(status, null, null, null, null, null, "desc"), page, size);
    }

    @GetMapping("/summary")
    public Summary summary(
            @RequestParam(required = false) String from, @RequestParam(required = false) String to, @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.VIEW);
        return queries.summary(caller(jwt), month(from), month(to));
    }

    @GetMapping("/no-earner")
    public List<NoEarnerRow> noEarner(@AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.VIEW);
        if (!access.orgWide(caller(jwt))) {
            throw new AccessDeniedException("Only Admin and Director see Schools without an earner.");
        }
        return queries.noEarner();
    }

    @GetMapping("/export.csv")
    public ResponseEntity<byte[]> export(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID managerId,
            @RequestParam(required = false) UUID zoneId,
            @RequestParam(required = false) String month,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "desc") String dir,
            @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.EXPORT);
        IncentiveCsv.CsvFile file = csv.export(caller(jwt), filter(status, managerId, zoneId, month, q, sort, dir));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv; charset=utf-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(file.fileName()).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(CacheControl.noStore())
                .body(file.bytes());
    }

    @GetMapping("/{id}")
    public Detail detail(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.VIEW);
        return queries.detail(caller(jwt), id);
    }

    @PostMapping("/{id}/approve")
    public Detail approve(@PathVariable UUID id, @RequestBody DecisionRequest request, @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.APPROVE);
        decisions.approve(caller(jwt), id, request.version());
        return queries.detail(caller(jwt), id);
    }

    @PostMapping("/{id}/reject")
    public Detail reject(@PathVariable UUID id, @RequestBody DecisionRequest request, @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.APPROVE);
        decisions.reject(caller(jwt), id, request.version(), request.reason());
        return queries.detail(caller(jwt), id);
    }

    @PostMapping("/{id}/reopen")
    public Detail reopen(@PathVariable UUID id, @RequestBody DecisionRequest request, @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.APPROVE);
        decisions.reopen(caller(jwt), id, request.version(), request.reason());
        return queries.detail(caller(jwt), id);
    }

    // ------------------------------------------------------------------ helpers

    private void require(Jwt jwt, PermissionAction action) {
        guard.require(CallerContext.roles(jwt), PermissionModule.MANAGER_INCENTIVES, action);
    }

    private static Caller caller(Jwt jwt) {
        return new Caller(CallerContext.userId(jwt), CallerContext.roles(jwt));
    }

    private static Filter filter(String status, UUID managerId, UUID zoneId, String month, String q, String sort, String dir) {
        IncentiveStatus parsed = null;
        if (status != null && !status.isBlank()) {
            try {
                parsed = IncentiveStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new InvalidInputException("The status is not one of the incentive statuses.");
            }
        }
        return new Filter(parsed, managerId, zoneId, month(month), q, sort, !"asc".equalsIgnoreCase(dir));
    }

    private static YearMonth month(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return YearMonth.parse(text.trim());
        } catch (DateTimeParseException e) {
            throw new InvalidInputException("The month must look like 2026-10.");
        }
    }
}
