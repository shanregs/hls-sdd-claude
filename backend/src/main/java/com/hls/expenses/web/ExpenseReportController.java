package com.hls.expenses.web;

import com.hls.expenses.internal.ClaimCsvWriter;
import com.hls.expenses.internal.CostMeasureService;
import com.hls.expenses.internal.PendingQueries;
import com.hls.identity.permissions.PermissionAction;
import com.hls.identity.permissions.PermissionGuard;
import com.hls.identity.permissions.PermissionModule;
import com.hls.school.api.CallerContext;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Pending approvals, the two cost measures and the monthly CSV (contracts/expenses-api.md), gated by the EXPENSES grants. */
@RestController
@RequestMapping("/api/v1/expenses")
public class ExpenseReportController {

    private final PendingQueries pending;
    private final CostMeasureService costs;
    private final ClaimCsvWriter csv;
    private final PermissionGuard guard;

    public ExpenseReportController(PendingQueries pending, CostMeasureService costs, ClaimCsvWriter csv, PermissionGuard guard) {
        this.pending = pending;
        this.costs = costs;
        this.csv = csv;
        this.guard = guard;
    }

    private void require(Jwt jwt, PermissionAction action) {
        guard.require(CallerContext.roles(jwt), PermissionModule.EXPENSES, action);
    }

    @GetMapping("/pending")
    public PendingQueries.Summary pending(@AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.VIEW);
        return pending.summary();
    }

    @GetMapping("/costs/per-school")
    public CostMeasureService.Measure perSchool(
            @RequestParam LocalDate from,
            @RequestParam LocalDate to,
            @RequestParam(required = false) UUID zoneId,
            @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.VIEW);
        return costs.perSchool(from, to, zoneId);
    }

    @GetMapping("/costs/per-teacher")
    public CostMeasureService.Measure perTeacher(
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(required = false) UUID batchId,
            @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.VIEW);
        return costs.perTeacher(from, to, batchId);
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(@RequestParam String month, @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.EXPORT);
        ClaimCsvWriter.CsvFile file = csv.export(CallerContext.userId(jwt), ExpenseBudgetController.month(month));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv; charset=utf-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(file.fileName()).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(CacheControl.noStore())
                .body(file.bytes());
    }
}
