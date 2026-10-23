package com.hls.expenses.internal;

import com.hls.identity.permissions.PermissionGuard;
import com.hls.identity.user.AppUser;
import com.hls.identity.user.AppUserRepository;
import com.hls.payroll.api.ReimbursementIntake;
import com.hls.payroll.api.ReimbursementIntake.OfferStatus;
import com.hls.school.api.SchoolDirectory;
import com.hls.school.api.SchoolDirectory.ZoneInfo;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Turns claims into what the screens and the Android app read (spec 015 contracts/expenses-api.md): money as a string
 * with two decimals, names instead of ids where a person reads them, and the {@code allowedActions} the viewer may take
 * (FR-031). One place, so My Expenses and the review screens agree.
 */
@Component
public class ClaimViews {

    public static final String HEAD_OFFICE = "Head office";

    public record LinkView(String kind, String kindLabel, UUID ref, LocalDate date, String label) {}

    public record DecisionView(UUID by, String byName, Instant at, String reason) {}

    public record PaymentView(
            String how,
            LocalDate paidOn,
            String mode,
            String reference,
            UUID payrollRunId,
            LocalDate payrollMonth,
            String recordedByName) {}

    public record BillView(
            UUID id,
            String name,
            String contentType,
            Long sizeBytes,
            UUID addedBy,
            String addedByName,
            Instant addedAt,
            boolean removed,
            String removedByName,
            Instant removedAt,
            String removalReason) {}

    public record HistoryView(Instant at, UUID actorId, String actorName, String event, String reason, String detail) {}

    public record Detail(
            UUID id,
            UUID claimantId,
            String claimantName,
            LocalDate expenseDate,
            String amount,
            String category,
            String categoryLabel,
            UUID zoneId,
            String areaName,
            String note,
            String paidBy,
            String reason,
            String noBillReason,
            LinkView link,
            String status,
            String statusLabel,
            Instant submittedAt,
            Long ageDays,
            DecisionView decision,
            PaymentView payment,
            String payrollState,
            List<BillView> bills,
            List<HistoryView> history,
            List<String> allowedActions,
            boolean ownClaim,
            long version,
            Instant createdAt) {}

    public record Row(
            UUID id,
            UUID claimantId,
            String claimantName,
            LocalDate expenseDate,
            String amount,
            String category,
            String categoryLabel,
            UUID zoneId,
            String areaName,
            String status,
            String statusLabel,
            String paidBy,
            int billCount,
            String noBillReason,
            String linkLabel,
            String note,
            Instant submittedAt,
            Long ageDays,
            String remainingBudget,
            List<String> allowedActions) {

        public Row withRemainingBudget(String remaining) {
            return new Row(
                    id, claimantId, claimantName, expenseDate, amount, category, categoryLabel, zoneId, areaName, status,
                    statusLabel, paidBy, billCount, noBillReason, linkLabel, note, submittedAt, ageDays, remaining, allowedActions);
        }
    }

    private final AppUserRepository users;
    private final SchoolDirectory zones;
    private final ExpenseBillRepository bills;
    private final ClaimEventRepository events;
    private final ReimbursementIntake payroll;
    private final PermissionGuard guard;
    private final com.hls.designation.api.BusinessDate businessDate;

    ClaimViews(
            AppUserRepository users,
            SchoolDirectory zones,
            ExpenseBillRepository bills,
            ClaimEventRepository events,
            ReimbursementIntake payroll,
            PermissionGuard guard,
            com.hls.designation.api.BusinessDate businessDate) {
        this.users = users;
        this.zones = zones;
        this.bills = bills;
        this.events = events;
        this.payroll = payroll;
        this.guard = guard;
        this.businessDate = businessDate;
    }

    // ------------------------------------------------------------------ detail

    public Detail detail(ExpenseCaller viewer, ExpenseClaim c) {
        List<ExpenseBill> billRows = bills.findByClaimIdOrderByAddedAt(c.getId());
        List<ClaimEvent> eventRows = events.findByClaimIdOrderByAtAscIdAsc(c.getId());
        Set<UUID> userIds = new HashSet<>();
        userIds.add(c.getClaimantId());
        addIfPresent(userIds, c.getDecidedBy());
        addIfPresent(userIds, c.getPaidByUser());
        billRows.forEach(b -> {
            addIfPresent(userIds, b.getAddedBy());
            addIfPresent(userIds, b.getRemovedBy());
        });
        eventRows.forEach(e -> addIfPresent(userIds, e.getActorId()));
        Map<UUID, String> names = names(userIds);
        String payrollState = payrollState(c);
        boolean own = c.getClaimantId().equals(viewer.userId());
        List<String> actions = AllowedActions.of(
                own,
                c.getStatus(),
                c.getPaidBy(),
                "HELD_AWAITING_APPROVAL".equals(payrollState) || "APPLIED".equals(payrollState),
                (module, action) -> guard.isGranted(viewer.roles(), module, action));
        return new Detail(
                c.getId(),
                c.getClaimantId(),
                names.getOrDefault(c.getClaimantId(), "Unknown user"),
                c.getExpenseDate(),
                money(c.getAmount()),
                c.getCategory().name(),
                c.getCategory().label(),
                c.getZoneId(),
                areaName(c.getZoneId(), zoneNames(List.of(c))),
                c.getNote(),
                c.getPaidBy().name(),
                c.getReason(),
                c.getNoBillReason(),
                link(c),
                c.getStatus().name(),
                c.getStatus().label(),
                c.getSubmittedAt(),
                c.getStatus() == ClaimStatus.SUBMITTED ? ageDays(c) : null,
                decision(c, names),
                payment(c, names),
                payrollState,
                billRows.stream().map(b -> bill(b, names)).toList(),
                eventRows.stream()
                        .map(e -> new HistoryView(
                                e.getAt(),
                                e.getActorId(),
                                e.getActorId() == null ? "System" : names.getOrDefault(e.getActorId(), "Unknown user"),
                                e.getEvent(),
                                e.getReason(),
                                e.getDetail()))
                        .toList(),
                actions,
                own,
                c.getVersion(),
                c.getCreatedAt());
    }

    // ------------------------------------------------------------------ rows

    public List<Row> rows(ExpenseCaller viewer, List<ExpenseClaim> claims) {
        if (claims.isEmpty()) {
            return List.of();
        }
        Set<UUID> ids = claims.stream().map(ExpenseClaim::getId).collect(Collectors.toSet());
        Map<UUID, Integer> billCounts = new HashMap<>();
        for (ExpenseBill b : bills.findByClaimIdInOrderByAddedAt(ids)) {
            if (b.isLive()) {
                billCounts.merge(b.getClaimId(), 1, Integer::sum);
            }
        }
        Map<UUID, String> names = names(claims.stream().map(ExpenseClaim::getClaimantId).collect(Collectors.toSet()));
        Map<UUID, String> zoneNames = zoneNames(claims);
        return claims.stream()
                .map(c -> {
                    boolean own = c.getClaimantId().equals(viewer.userId());
                    return new Row(
                            c.getId(),
                            c.getClaimantId(),
                            names.getOrDefault(c.getClaimantId(), "Unknown user"),
                            c.getExpenseDate(),
                            money(c.getAmount()),
                            c.getCategory().name(),
                            c.getCategory().label(),
                            c.getZoneId(),
                            areaName(c.getZoneId(), zoneNames),
                            c.getStatus().name(),
                            c.getStatus().label(),
                            c.getPaidBy().name(),
                            billCounts.getOrDefault(c.getId(), 0),
                            c.getNoBillReason(),
                            c.getLinkLabel(),
                            c.getNote(),
                            c.getSubmittedAt(),
                            c.getStatus() == ClaimStatus.SUBMITTED ? ageDays(c) : null,
                            null,
                            AllowedActions.of(
                                    own,
                                    c.getStatus(),
                                    c.getPaidBy(),
                                    false,
                                    (module, action) -> guard.isGranted(viewer.roles(), module, action)));
                })
                .toList();
    }

    // ------------------------------------------------------------------ helpers

    static String money(BigDecimal amount) {
        return amount.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    private static void addIfPresent(Set<UUID> ids, UUID id) {
        if (id != null) {
            ids.add(id);
        }
    }

    Map<UUID, String> names(Collection<UUID> ids) {
        Map<UUID, String> map = new HashMap<>();
        if (!ids.isEmpty()) {
            for (AppUser user : users.findAllById(ids)) {
                map.put(user.getId(), user.getDisplayName());
            }
        }
        return map;
    }

    private Map<UUID, String> zoneNames(Collection<ExpenseClaim> claims) {
        Set<UUID> ids = claims.stream().map(ExpenseClaim::getZoneId).filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        return zones.zones(ids).stream().collect(Collectors.toMap(ZoneInfo::id, ZoneInfo::name, (a, b) -> a, HashMap::new));
    }

    static String areaName(UUID zoneId, Map<UUID, String> zoneNames) {
        return zoneId == null ? HEAD_OFFICE : zoneNames.getOrDefault(zoneId, "Unknown Zone");
    }

    /** Whole days since submission, in the business time zone. */
    private Long ageDays(ExpenseClaim c) {
        if (c.getSubmittedAt() == null) {
            return null;
        }
        LocalDate submitted = c.getSubmittedAt().atZone(java.time.ZoneId.of("Asia/Kolkata")).toLocalDate();
        return Math.max(0, java.time.temporal.ChronoUnit.DAYS.between(submitted, businessDate.today()));
    }

    private String payrollState(ExpenseClaim c) {
        if (c.getStatus() != ClaimStatus.APPROVED && c.getStatus() != ClaimStatus.PAID) {
            return OfferStatus.NONE.name();
        }
        OfferStatus status = payroll.status(c.getId().toString());
        return status == OfferStatus.WITHDRAWN ? OfferStatus.NONE.name() : status.name();
    }

    private static LinkView link(ExpenseClaim c) {
        if (c.getLinkKind() == null) {
            return null;
        }
        return new LinkView(c.getLinkKind(), LinkKinds.label(c.getLinkKind()), c.getLinkRef(), c.getLinkDate(), c.getLinkLabel());
    }

    private static DecisionView decision(ExpenseClaim c, Map<UUID, String> names) {
        if (c.getDecidedBy() == null) {
            return null;
        }
        return new DecisionView(
                c.getDecidedBy(), names.getOrDefault(c.getDecidedBy(), "Unknown user"), c.getDecidedAt(), c.getDecisionReason());
    }

    private static PaymentView payment(ExpenseClaim c, Map<UUID, String> names) {
        if (c.getStatus() != ClaimStatus.PAID) {
            return null;
        }
        return new PaymentView(
                c.getPaidHow().name(),
                c.getPaidOn(),
                c.getPaidMode() == null ? null : c.getPaidMode().name(),
                c.getPaidReference(),
                c.getPayrollRunId(),
                c.getPayrollMonth(),
                c.getPaidByUser() == null ? null : names.getOrDefault(c.getPaidByUser(), "Unknown user"));
    }

    private static BillView bill(ExpenseBill b, Map<UUID, String> names) {
        return new BillView(
                b.getId(),
                b.getName(),
                b.getContentType(),
                b.getSizeBytes(),
                b.getAddedBy(),
                names.getOrDefault(b.getAddedBy(), "Unknown user"),
                b.getAddedAt(),
                !b.isLive(),
                b.getRemovedBy() == null ? null : names.getOrDefault(b.getRemovedBy(), "Unknown user"),
                b.getRemovedAt(),
                b.getRemovalReason());
    }
}
