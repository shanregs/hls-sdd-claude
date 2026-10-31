package com.hls.expenses.internal;

import com.hls.designation.api.BusinessDate;
import com.hls.expenses.internal.ClaimService.ClaimRequest;
import com.hls.identity.user.AppUser;
import com.hls.identity.user.AppUserRepository;
import com.hls.identity.user.Role;
import com.hls.school.api.ConflictException;
import com.hls.school.api.SchoolDirectory;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Dev-only demo data (inert unless {@code hls.seed.demo-data=true}, idempotent through the request id of each claim):
 * claims in every status for the demo Manager, Teacher and Admin, each with a generated jpg bill, a budget for the first
 * Zone for the current month, and one approved personally paid claim for the demo Manager waiting for payroll. It goes
 * through the real services, and never changes an existing claim.
 */
@Component
@Order(90)
@ConditionalOnProperty(name = "hls.seed.demo-data", havingValue = "true")
class ExpensesDevSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ExpensesDevSeeder.class);

    /** The first bytes of a JPEG: enough for the file store content check. */
    private static final byte[] JPG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16, 'J', 'F', 'I', 'F', 0, 1, 1, 0};

    private final ClaimService claims;
    private final BillService bills;
    private final DecisionService decisions;
    private final BudgetService budgets;
    private final AppUserRepository users;
    private final SchoolDirectory zones;
    private final BusinessDate businessDate;

    ExpensesDevSeeder(
            ClaimService claims,
            BillService bills,
            DecisionService decisions,
            BudgetService budgets,
            AppUserRepository users,
            SchoolDirectory zones,
            BusinessDate businessDate) {
        this.claims = claims;
        this.bills = bills;
        this.decisions = decisions;
        this.budgets = budgets;
        this.users = users;
        this.zones = zones;
        this.businessDate = businessDate;
    }

    @Override
    public void run(ApplicationArguments args) {
        Optional<AppUser> admin = users.findByPhone("9800000001");
        Optional<AppUser> director = users.findByPhone("9800000002");
        Optional<AppUser> manager = users.findByPhone("9800000003");
        Optional<AppUser> teacher = users.findByPhone("9800000004");
        if (admin.isEmpty() || director.isEmpty() || manager.isEmpty() || teacher.isEmpty()) {
            return;
        }
        ExpenseCaller asAdmin = new ExpenseCaller(admin.get().getId(), Set.of(Role.ADMIN));
        ExpenseCaller asDirector = new ExpenseCaller(director.get().getId(), Set.of(Role.DIRECTOR));
        ExpenseCaller asManager = new ExpenseCaller(manager.get().getId(), Set.of(Role.MANAGER));
        ExpenseCaller asTeacher = new ExpenseCaller(teacher.get().getId(), Set.of(Role.TEACHER));
        UUID zone = zones.allZones().stream().findFirst().map(SchoolDirectory.ZoneInfo::id).orElse(null);
        LocalDate day = businessDate.today().minusDays(1);

        int added = 0;
        // Manager: a Draft, a Submitted, an Approved waiting for payroll, a Rejected and one paid by the company
        UUID draft = claim(asManager, 1, day, "450.00", "OFFICE_SUPPLIES", zone, "CLAIMANT", null, false);
        added += draft == null ? 0 : 1;
        UUID submitted = claim(asManager, 2, day, "850.00", "TRAVEL_FUEL", zone, "CLAIMANT", null, true);
        added += submitted == null ? 0 : 1;
        UUID approved = claim(asManager, 3, day, "1200.00", "TRAVEL_FUEL", zone, "CLAIMANT", null, true);
        if (approved != null) {
            decisions.approve(asDirector, approved, versionOf(approved));
            added++;
        }
        UUID rejected = claim(asManager, 4, day, "300.00", "OTHER", zone, "CLAIMANT", "Courier charge", true);
        if (rejected != null) {
            decisions.reject(asAdmin, rejected, versionOf(rejected), "The bill is for another month");
            added++;
        }
        UUID company = claim(asManager, 5, day, "2000.00", "RENT_UTILITIES", zone, "COMPANY", null, true);
        if (company != null) {
            decisions.approve(asDirector, company, versionOf(company));
            added++;
        }
        // Teacher and Admin: one submitted claim each, waiting for a reviewer
        added += claim(asTeacher, 6, day, "200.00", "TRAVEL_FUEL", zone, "CLAIMANT", null, true) == null ? 0 : 1;
        added += claim(asAdmin, 7, day, "999.00", "OFFICE_SUPPLIES", null, "CLAIMANT", null, true) == null ? 0 : 1;

        if (zone != null) {
            YearMonth month = YearMonth.from(day);
            budgets.set(asDirector, zone, month.toString(), new BigDecimal("20000.00"), null);
        }
        if (added > 0) {
            log.info("[DEV SEED] Expenses demo data ready: {} claim(s) added.", added);
        }
    }

    private final java.util.Map<UUID, Long> versions = new java.util.HashMap<>();

    private long versionOf(UUID claimId) {
        return versions.getOrDefault(claimId, 0L);
    }

    /** Creates the numbered demo claim once; returns its id when this call created it, null when it already existed. */
    private UUID claim(
            ExpenseCaller who,
            int number,
            LocalDate date,
            String amount,
            String category,
            UUID zone,
            String paidBy,
            String reason,
            boolean submit) {
        UUID requestId = UUID.nameUUIDFromBytes(("demo-expense-" + number).getBytes(StandardCharsets.UTF_8));
        ClaimRequest request = new ClaimRequest(
                requestId, date, new BigDecimal(amount), category, zone, "Demo claim " + number, paidBy, reason, null, null, false, false, null);
        ClaimService.Created created;
        try {
            created = claims.create(who, request);
        } catch (ConflictException e) {
            // seeded on an earlier day: the date moved on, the claim is still there
            return null;
        }
        if (!created.created()) {
            return null;
        }
        bills.add(who, created.id(), "demo-bill-" + number + ".jpg", JPG);
        if (submit) {
            claims.submit(who, created.id(), 0L, true);
            versions.put(created.id(), 1L);
        }
        return created.id();
    }
}
