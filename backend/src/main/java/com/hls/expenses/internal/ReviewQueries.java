package com.hls.expenses.internal;

import com.hls.expenses.internal.ClaimViews.Detail;
import com.hls.expenses.internal.ClaimViews.Row;
import com.hls.school.api.InvalidInputException;
import com.hls.school.api.NotFoundException;
import com.hls.school.api.PageResponse;
import jakarta.persistence.criteria.Predicate;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * What Admin and Director read about everyone claims (spec 015 US3, FR-012): the queue oldest first with age and the
 * area remaining budget for the claim month. Someone else Draft is private work and is never listed or opened.
 */
@Service
@Transactional(readOnly = true)
public class ReviewQueries {

    static final int MAX_PAGE_SIZE = 200;
    static final String HEAD_OFFICE_PARAM = "head-office";

    private final ExpenseClaimRepository claims;
    private final ClaimViews views;
    private final BudgetQueries budgets;

    ReviewQueries(ExpenseClaimRepository claims, ClaimViews views, BudgetQueries budgets) {
        this.claims = claims;
        this.views = views;
        this.budgets = budgets;
    }

    public PageResponse<Row> list(
            ExpenseCaller caller,
            String status,
            UUID claimant,
            String zone,
            String category,
            LocalDate from,
            LocalDate to,
            int page,
            int size) {
        boolean allStatuses = status != null && "ALL".equalsIgnoreCase(status.trim());
        ClaimStatus wanted = allStatuses || status == null || status.isBlank()
                ? (allStatuses ? null : ClaimStatus.SUBMITTED)
                : MyExpensesQueries.parseStatus(status);
        if (wanted == ClaimStatus.DRAFT) {
            return new PageResponse<>(List.of(), 0, Math.max(1, Math.min(size, MAX_PAGE_SIZE)), 0);
        }
        ExpenseCategory wantedCategory = category == null || category.isBlank() ? null : ExpenseInput.category(category);
        boolean headOffice = zone != null && HEAD_OFFICE_PARAM.equalsIgnoreCase(zone.trim());
        UUID zoneId = zone == null || zone.isBlank() || headOffice ? null : parseZone(zone);
        Specification<ExpenseClaim> spec = (root, query, cb) -> {
            List<Predicate> all = new ArrayList<>();
            if (allStatuses) {
                all.add(cb.notEqual(root.get("status"), ClaimStatus.DRAFT));
            } else {
                all.add(cb.equal(root.get("status"), wanted));
            }
            if (claimant != null) {
                all.add(cb.equal(root.get("claimantId"), claimant));
            }
            if (headOffice) {
                all.add(cb.isNull(root.get("zoneId")));
            } else if (zoneId != null) {
                all.add(cb.equal(root.get("zoneId"), zoneId));
            }
            if (wantedCategory != null) {
                all.add(cb.equal(root.get("category"), wantedCategory));
            }
            if (from != null) {
                all.add(cb.greaterThanOrEqualTo(root.get("expenseDate"), from));
            }
            if (to != null) {
                all.add(cb.lessThanOrEqualTo(root.get("expenseDate"), to));
            }
            return cb.and(all.toArray(new Predicate[0]));
        };
        Sort sort = !allStatuses && wanted == ClaimStatus.SUBMITTED
                ? Sort.by(Sort.Order.asc("submittedAt"), Sort.Order.asc("id"))
                : Sort.by(Sort.Order.desc("expenseDate"), Sort.Order.desc("createdAt"));
        Page<ExpenseClaim> found = claims.findAll(
                spec, PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, MAX_PAGE_SIZE)), sort));
        Map<String, String> remaining = new HashMap<>();
        List<Row> rows = new ArrayList<>();
        List<ExpenseClaim> content = found.getContent();
        List<Row> base = views.rows(caller, content);
        for (int i = 0; i < base.size(); i++) {
            ExpenseClaim c = content.get(i);
            String key = c.getZoneId() + "|" + YearMonth.from(c.getExpenseDate());
            String left = remaining.containsKey(key)
                    ? remaining.get(key)
                    : remaining.computeIfAbsent(key, k -> budgets.remaining(c.getZoneId(), YearMonth.from(c.getExpenseDate())));
            rows.add(base.get(i).withRemainingBudget(left));
        }
        return new PageResponse<>(rows, found.getNumber(), found.getSize(), found.getTotalElements());
    }

    public Detail detail(ExpenseCaller caller, UUID claimId) {
        return views.detail(caller, visible(caller, claimId));
    }

    /** A claim Admin or Director may open: any but someone else Draft. */
    ExpenseClaim visible(ExpenseCaller caller, UUID claimId) {
        return claims.findById(claimId)
                .filter(c -> c.getStatus() != ClaimStatus.DRAFT || c.getClaimantId().equals(caller.userId()))
                .orElseThrow(() -> new NotFoundException("Claim not found."));
    }

    private static UUID parseZone(String zone) {
        try {
            return UUID.fromString(zone.trim().toLowerCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new InvalidInputException("The zone must be a Zone id or head-office.");
        }
    }
}
