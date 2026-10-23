package com.hls.expenses.internal;

import com.hls.designation.api.BusinessDate;
import com.hls.expenses.internal.ClaimViews.Detail;
import com.hls.expenses.internal.ClaimViews.Row;
import com.hls.school.api.InvalidInputException;
import com.hls.school.api.NotFoundException;
import com.hls.school.api.SchoolDirectory;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** What a claimant reads about their own claims (spec 015 US7): the list with what they are still owed, and one claim. */
@Service
@Transactional(readOnly = true)
public class MyExpensesQueries {

    static final int MAX_PAGE_SIZE = 100;

    /** One page of the caller own claims, newest first, with the total they are still owed. */
    public record MyList(List<Row> content, int page, int size, long totalElements, String owed) {}

    public record LinkOption(String kind, String kindLabel, UUID ref, LocalDate date, String label) {}

    public record Area(UUID id, String name) {}

    private final ExpenseClaimRepository claims;
    private final ClaimViews views;
    private final LinkResolver links;
    private final SchoolDirectory zones;
    private final BusinessDate businessDate;
    private final JdbcTemplate jdbc;

    MyExpensesQueries(
            ExpenseClaimRepository claims,
            ClaimViews views,
            LinkResolver links,
            SchoolDirectory zones,
            BusinessDate businessDate,
            JdbcTemplate jdbc) {
        this.claims = claims;
        this.views = views;
        this.links = links;
        this.zones = zones;
        this.businessDate = businessDate;
        this.jdbc = jdbc;
    }

    public MyList list(ExpenseCaller caller, String status, String month, int page, int size) {
        ClaimStatus wanted = parseStatus(status);
        YearMonth wantedMonth = parseMonth(month);
        Specification<ExpenseClaim> spec = (root, query, cb) -> {
            List<Predicate> all = new ArrayList<>();
            all.add(cb.equal(root.get("claimantId"), caller.userId()));
            if (wanted != null) {
                all.add(cb.equal(root.get("status"), wanted));
            }
            if (wantedMonth != null) {
                all.add(cb.between(root.get("expenseDate"), wantedMonth.atDay(1), wantedMonth.atEndOfMonth()));
            }
            return cb.and(all.toArray(new Predicate[0]));
        };
        Page<ExpenseClaim> found = claims.findAll(
                spec,
                PageRequest.of(
                        Math.max(0, page),
                        Math.max(1, Math.min(size, MAX_PAGE_SIZE)),
                        Sort.by(Sort.Order.desc("expenseDate"), Sort.Order.desc("createdAt"))));
        return new MyList(
                views.rows(caller, found.getContent()),
                found.getNumber(),
                found.getSize(),
                found.getTotalElements(),
                ClaimViews.money(owed(caller.userId())));
    }

    /** The sum of own Approved, personally paid claims that are not yet paid. */
    BigDecimal owed(UUID claimant) {
        BigDecimal sum = jdbc.queryForObject(
                "select coalesce(sum(amount), 0) from expense_claim where claimant_id = ? and status = 'APPROVED' and paid_by = 'CLAIMANT'",
                BigDecimal.class,
                claimant);
        return sum == null ? BigDecimal.ZERO : sum;
    }

    public Detail detail(ExpenseCaller caller, UUID claimId) {
        return views.detail(caller, own(caller, claimId));
    }

    /** The caller own claim; anyone else sees "not found" (FR-027). */
    ExpenseClaim own(ExpenseCaller caller, UUID claimId) {
        return claims.findById(claimId)
                .filter(c -> c.getClaimantId().equals(caller.userId()))
                .orElseThrow(() -> new NotFoundException("Claim not found."));
    }

    /** What a claim of the category and date may be linked to (US2). */
    public List<LinkOption> linkOptions(ExpenseCaller caller, String category, LocalDate date) {
        ExpenseCategory parsed = ExpenseInput.category(category);
        LocalDate on = date == null ? businessDate.today() : date;
        return links.options(caller.userId(), parsed, on).stream()
                .map(o -> new LinkOption(o.kind(), LinkKinds.label(o.kind()), o.ref(), o.date(), o.label()))
                .toList();
    }

    /** The Zones and Head office a claim can be raised against. */
    public List<Area> areas() {
        List<Area> areas = new ArrayList<>();
        areas.add(new Area(null, ClaimViews.HEAD_OFFICE));
        zones.allZones().forEach(z -> areas.add(new Area(z.id(), z.name())));
        return areas;
    }

    static ClaimStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return ClaimStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new InvalidInputException("Unknown status " + status.trim() + ".");
        }
    }

    static YearMonth parseMonth(String month) {
        if (month == null || month.isBlank()) {
            return null;
        }
        try {
            return YearMonth.parse(month.trim());
        } catch (DateTimeParseException e) {
            throw new InvalidInputException("The month must look like 2026-10.");
        }
    }
}
