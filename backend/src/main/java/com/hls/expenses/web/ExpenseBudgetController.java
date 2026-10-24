package com.hls.expenses.web;

import com.hls.expenses.internal.BudgetQueries;
import com.hls.expenses.internal.BudgetService;
import com.hls.expenses.internal.ExpenseCaller;
import com.hls.identity.permissions.PermissionAction;
import com.hls.identity.permissions.PermissionGuard;
import com.hls.identity.permissions.PermissionModule;
import com.hls.school.api.CallerContext;
import com.hls.school.api.InvalidInputException;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Budget versus actual per area and month (contracts/expenses-api.md), gated by the EXPENSES grants. */
@RestController
@RequestMapping("/api/v1/expenses/budgets")
public class ExpenseBudgetController {

    private final BudgetQueries queries;
    private final BudgetService service;
    private final PermissionGuard guard;

    public ExpenseBudgetController(BudgetQueries queries, BudgetService service, PermissionGuard guard) {
        this.queries = queries;
        this.service = service;
        this.guard = guard;
    }

    public record BudgetBody(UUID zoneId, String month, BigDecimal amount, Long version) {}

    public record SavedBudget(UUID budgetId, boolean created) {}

    private void require(Jwt jwt, PermissionAction action) {
        guard.require(CallerContext.roles(jwt), PermissionModule.EXPENSES, action);
    }

    static YearMonth month(String text) {
        if (text == null || text.isBlank()) {
            throw new InvalidInputException("The month is required, like 2026-10.");
        }
        try {
            return YearMonth.parse(text.trim());
        } catch (DateTimeParseException e) {
            throw new InvalidInputException("The month must look like 2026-10.");
        }
    }

    @GetMapping
    public BudgetQueries.Overview overview(@RequestParam String month, @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.VIEW);
        return queries.overview(month(month));
    }

    @GetMapping("/detail")
    public BudgetQueries.Detail detail(
            @RequestParam String month, @RequestParam(required = false) String zoneId, @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.VIEW);
        if (zoneId == null || zoneId.isBlank()) {
            return queries.detail(month(month), true, null);
        }
        if ("head-office".equalsIgnoreCase(zoneId.trim())) {
            return queries.detail(month(month), false, null);
        }
        try {
            return queries.detail(month(month), false, UUID.fromString(zoneId.trim()));
        } catch (IllegalArgumentException e) {
            throw new InvalidInputException("The zone must be a Zone id or head-office.");
        }
    }

    @PutMapping
    public SavedBudget set(@RequestBody BudgetBody body, @AuthenticationPrincipal Jwt jwt) {
        if (body == null) {
            throw new InvalidInputException("The budget is required.");
        }
        require(jwt, PermissionAction.VIEW);
        // an existing budget is changed (Edit); a new one is set (Create)
        require(jwt, service.exists(body.zoneId(), body.month()) ? PermissionAction.EDIT : PermissionAction.CREATE);
        ExpenseCaller caller = new ExpenseCaller(CallerContext.userId(jwt), CallerContext.roles(jwt));
        BudgetService.Saved saved = service.set(caller, body.zoneId(), body.month(), body.amount(), body.version());
        return new SavedBudget(saved.budgetId(), saved.created());
    }

    @GetMapping("/{id}/history")
    public List<BudgetQueries.ChangeView> history(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.VIEW);
        return queries.history(id);
    }
}
