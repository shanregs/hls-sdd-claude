package com.hls.expenses.web;

import com.hls.expenses.internal.BillService;
import com.hls.expenses.internal.ClaimViews.Detail;
import com.hls.expenses.internal.ClaimViews.Row;
import com.hls.expenses.internal.DecisionService;
import com.hls.expenses.internal.ExpenseCaller;
import com.hls.expenses.internal.ExpensePaymentService;
import com.hls.expenses.internal.ReviewQueries;
import com.hls.identity.permissions.PermissionAction;
import com.hls.identity.permissions.PermissionGuard;
import com.hls.identity.permissions.PermissionModule;
import com.hls.school.api.CallerContext;
import com.hls.school.api.PageResponse;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Review and payment of claims (contracts/expenses-api.md), gated by the EXPENSES grants. */
@RestController
@RequestMapping("/api/v1/expenses")
public class ExpenseReviewController {

    private final ReviewQueries queries;
    private final DecisionService decisions;
    private final ExpensePaymentService payments;
    private final BillService bills;
    private final PermissionGuard guard;

    public ExpenseReviewController(
            ReviewQueries queries, DecisionService decisions, ExpensePaymentService payments, BillService bills, PermissionGuard guard) {
        this.queries = queries;
        this.decisions = decisions;
        this.payments = payments;
        this.bills = bills;
        this.guard = guard;
    }

    public record VersionBody(Long version) {}

    public record ReasonVersionBody(Long version, String reason) {}

    public record ReasonBody(String reason) {}

    public record PaidBody(Long version, LocalDate paidOn, String mode, String reference) {}

    private void require(Jwt jwt, PermissionAction action) {
        guard.require(CallerContext.roles(jwt), PermissionModule.EXPENSES, action);
    }

    private static ExpenseCaller caller(Jwt jwt) {
        return new ExpenseCaller(CallerContext.userId(jwt), CallerContext.roles(jwt));
    }

    @GetMapping
    public PageResponse<Row> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID claimant,
            @RequestParam(required = false) String zone,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.VIEW);
        return queries.list(caller(jwt), status, claimant, zone, category, from, to, page, size);
    }

    @GetMapping("/{id}")
    public Detail get(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.VIEW);
        return queries.detail(caller(jwt), id);
    }

    @GetMapping("/{id}/bills/{billId}")
    public ResponseEntity<byte[]> downloadBill(@PathVariable UUID id, @PathVariable UUID billId, @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.VIEW);
        queries.detail(caller(jwt), id);
        return BillResponses.download(bills.open(id, billId));
    }

    @PostMapping("/{id}/approve")
    public Detail approve(@PathVariable UUID id, @RequestBody VersionBody body, @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.APPROVE);
        ExpenseCaller caller = caller(jwt);
        decisions.approve(caller, id, body == null ? null : body.version());
        return queries.detail(caller, id);
    }

    @PostMapping("/{id}/reject")
    public Detail reject(@PathVariable UUID id, @RequestBody ReasonVersionBody body, @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.APPROVE);
        ExpenseCaller caller = caller(jwt);
        decisions.reject(caller, id, body == null ? null : body.version(), body == null ? null : body.reason());
        return queries.detail(caller, id);
    }

    @PostMapping("/{id}/reverse")
    public Detail reverse(@PathVariable UUID id, @RequestBody ReasonVersionBody body, @AuthenticationPrincipal Jwt jwt) {
        if (!guard.isGranted(CallerContext.roles(jwt), PermissionModule.EXPENSES, PermissionAction.APPROVE)
                && !guard.isGranted(CallerContext.roles(jwt), PermissionModule.EXPENSES, PermissionAction.PROCESS)) {
            throw new AccessDeniedException("Not authorized for EXPENSES.APPROVE or EXPENSES.PROCESS");
        }
        ExpenseCaller caller = caller(jwt);
        payments.reverse(caller, id, body == null ? null : body.version(), body == null ? null : body.reason());
        return queries.detail(caller, id);
    }

    @PostMapping("/{id}/record-paid")
    public Detail recordPaid(@PathVariable UUID id, @RequestBody PaidBody body, @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.PROCESS);
        ExpenseCaller caller = caller(jwt);
        payments.recordPaid(
                caller, id, body == null ? null : body.version(), body == null ? null : body.paidOn(),
                body == null ? null : body.mode(), body == null ? null : body.reference());
        return queries.detail(caller, id);
    }

    @DeleteMapping("/{id}/bills/{billId}")
    public Detail removeBill(
            @PathVariable UUID id, @PathVariable UUID billId, @RequestBody ReasonBody body, @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.APPROVE);
        ExpenseCaller caller = caller(jwt);
        bills.remove(caller, id, billId, body == null ? null : body.reason());
        return queries.detail(caller, id);
    }
}
