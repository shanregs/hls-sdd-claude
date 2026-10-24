package com.hls.expenses.internal;

import com.hls.designation.api.BusinessDate;
import com.hls.expenses.api.ExpenseSubmitted;
import com.hls.files.api.FileStore;
import com.hls.identity.user.AppUser;
import com.hls.identity.user.AppUserRepository;
import com.hls.school.api.ConflictException;
import com.hls.school.api.InvalidInputException;
import com.hls.school.api.NotFoundException;
import com.hls.school.api.SchoolDirectory;
import com.hls.school.api.StaleVersion;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The claimant side of a claim (spec 015 US1, US2, US7): create (idempotent on the request id), edit, delete a Draft,
 * submit, withdraw, and correct and resubmit a Rejected claim as the same claim. The server alone decides who may do
 * what; every change writes the claim history and Change History in the same transaction.
 */
@Service
public class ClaimService {

    /** A link a claimant chose: its kind and the id of the activity, task or batch. */
    public record LinkChoice(String kind, UUID ref) {}

    /** What the claimant sends to create or change a claim. */
    public record ClaimRequest(
            UUID clientRequestId,
            LocalDate expenseDate,
            BigDecimal amount,
            String category,
            UUID zoneId,
            String note,
            String paidBy,
            String reason,
            String noBillReason,
            LinkChoice link,
            Boolean confirmDuplicate,
            Boolean submit,
            Long version) {}

    /** The claim id and whether this call created it (false for a repeat of the same request id). */
    public record Created(UUID id, boolean created) {}

    private final ExpenseClaimRepository claims;
    private final ExpenseBillRepository bills;
    private final ClaimRecorder recorder;
    private final LinkResolver links;
    private final SchoolDirectory zones;
    private final AppUserRepository users;
    private final FileStore files;
    private final ApplicationEventPublisher events;
    private final BusinessDate businessDate;
    private final Clock clock;

    ClaimService(
            ExpenseClaimRepository claims,
            ExpenseBillRepository bills,
            ClaimRecorder recorder,
            LinkResolver links,
            SchoolDirectory zones,
            AppUserRepository users,
            FileStore files,
            ApplicationEventPublisher events,
            BusinessDate businessDate,
            Clock clock) {
        this.claims = claims;
        this.bills = bills;
        this.recorder = recorder;
        this.links = links;
        this.zones = zones;
        this.users = users;
        this.files = files;
        this.events = events;
        this.businessDate = businessDate;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ create

    @Transactional
    public Created create(ExpenseCaller caller, ClaimRequest request) {
        UUID requestId = ExpenseInput.requestId(request.clientRequestId());
        Optional<ExpenseClaim> existing = claims.findByClaimantIdAndClientRequestId(caller.userId(), requestId);
        if (existing.isPresent()) {
            ExpenseClaim first = existing.get();
            if (!sameBody(first, request)) {
                throw new ConflictException("This request id was already used for a different claim.");
            }
            return new Created(first.getId(), false);
        }
        ExpenseClaim claim = new ExpenseClaim(caller.userId(), requestId, clock.instant());
        apply(claim, request, caller);
        claims.saveAndFlush(claim);
        recorder.event(claim.getId(), caller.userId(), "CREATED", null, claim.getCategory().label() + " " + claim.getAmount());
        if (Boolean.TRUE.equals(request.submit())) {
            doSubmit(caller, claim, Boolean.TRUE.equals(request.confirmDuplicate()));
        }
        return new Created(claim.getId(), true);
    }

    private static boolean sameBody(ExpenseClaim first, ClaimRequest request) {
        return Objects.equals(first.getExpenseDate(), request.expenseDate())
                && request.amount() != null
                && first.getAmount().compareTo(request.amount()) == 0
                && first.getCategory().name().equalsIgnoreCase(request.category() == null ? "" : request.category().trim())
                && Objects.equals(first.getZoneId(), request.zoneId())
                && Objects.equals(first.getNote(), ExpenseInput.text(request.note(), ExpenseInput.MAX_NOTE, "The note"));
    }

    // ------------------------------------------------------------------ edit

    @Transactional
    public void edit(ExpenseCaller caller, UUID claimId, ClaimRequest request) {
        ExpenseClaim claim = ownLocked(caller, claimId);
        if (!claim.getStatus().editable()) {
            throw new ConflictException("Only a Draft or Rejected claim can be changed; this one is "
                    + claim.getStatus().label().toLowerCase() + ".");
        }
        StaleVersion.check(ExpenseClaim.class, claimId, claim.getVersion(), request.version());
        ExpenseClaim before = copyOfValues(claim);
        apply(claim, request, caller);
        List<String> changed = new ArrayList<>();
        track(caller, claim, "expenseDate", before.getExpenseDate(), claim.getExpenseDate(), changed);
        track(caller, claim, "amount", before.getAmount(), claim.getAmount(), changed);
        track(caller, claim, "category", before.getCategory(), claim.getCategory(), changed);
        track(caller, claim, "zoneId", before.getZoneId(), claim.getZoneId(), changed);
        track(caller, claim, "note", before.getNote(), claim.getNote(), changed);
        track(caller, claim, "paidBy", before.getPaidBy(), claim.getPaidBy(), changed);
        track(caller, claim, "reason", before.getReason(), claim.getReason(), changed);
        track(caller, claim, "noBillReason", before.getNoBillReason(), claim.getNoBillReason(), changed);
        track(caller, claim, "linkLabel", before.getLinkLabel(), claim.getLinkLabel(), changed);
        claims.saveAndFlush(claim);
        if (!changed.isEmpty()) {
            recorder.historyOnly(claimId, caller.userId(), "EDITED", null, String.join("; ", changed));
        }
        if (Boolean.TRUE.equals(request.submit())) {
            doSubmit(caller, claim, Boolean.TRUE.equals(request.confirmDuplicate()));
        }
    }

    private void track(ExpenseCaller caller, ExpenseClaim claim, String field, Object before, Object after, List<String> changed) {
        if (!Objects.equals(before, after)) {
            recorder.field(caller.userId(), claim.getId(), field, before, after);
            changed.add(field + ": " + (before == null ? "none" : before) + " -> " + (after == null ? "none" : after));
        }
    }

    /** A detached copy of the editable values, taken before they change. */
    private static ExpenseClaim copyOfValues(ExpenseClaim claim) {
        ExpenseClaim copy = new ExpenseClaim(claim.getClaimantId(), claim.getClientRequestId(), claim.getCreatedAt());
        copy.fill(
                claim.getExpenseDate(),
                claim.getAmount(),
                claim.getCategory(),
                claim.getZoneId(),
                claim.getNote(),
                claim.getPaidBy(),
                claim.getReason(),
                claim.getNoBillReason(),
                claim.getUpdatedAt());
        copy.snapshotLink(claim.getLinkKind(), claim.getLinkRef(), claim.getLinkDate(), claim.getLinkLabel());
        return copy;
    }

    /** Validates what a Draft needs (a date, a positive amount, a category) and sets the editable fields and the link. */
    private void apply(ExpenseClaim claim, ClaimRequest request, ExpenseCaller caller) {
        if (request.expenseDate() == null) {
            throw new InvalidInputException("The expense date is required.");
        }
        BigDecimal amount = ExpenseInput.amount(request.amount());
        ExpenseCategory category = ExpenseInput.category(request.category());
        PaidBy paidBy = ExpenseInput.paidBy(request.paidBy());
        String note = ExpenseInput.text(request.note(), ExpenseInput.MAX_NOTE, "The note");
        String reason = ExpenseInput.text(request.reason(), ExpenseInput.MAX_REASON, "The reason");
        String noBill = ExpenseInput.text(request.noBillReason(), ExpenseInput.MAX_REASON, "The no-bill reason");
        UUID zoneId = request.zoneId();
        if (zoneId != null && zones.zone(zoneId).isEmpty()) {
            throw new InvalidInputException("Choose a Zone or Head office for the area.");
        }
        claim.fill(request.expenseDate(), amount, category, zoneId, note, paidBy, reason, noBill, clock.instant());
        applyLink(claim, request.link(), caller);
    }

    private void applyLink(ExpenseClaim claim, LinkChoice choice, ExpenseCaller caller) {
        String kind = choice == null ? null : choice.kind();
        UUID ref = choice == null ? null : choice.ref();
        boolean unchanged = ref != null
                && claim.getLinkRef() != null
                && claim.getLinkRef().equals(ref)
                && Objects.equals(claim.getLinkKind(), kind == null ? null : kind.trim())
                && (claim.getCategory() == ExpenseCategory.TRAVEL_FUEL || claim.getCategory() == ExpenseCategory.TRAINING_STAY);
        if (unchanged) {
            return; // what it was linked to may be gone by now; the stored snapshot stays (FR-011)
        }
        Optional<LinkResolver.Option> option =
                links.resolve(caller.userId(), claim.getCategory(), claim.getExpenseDate(), kind, ref);
        if (option.isPresent()) {
            LinkResolver.Option o = option.get();
            claim.snapshotLink(o.kind(), o.ref(), o.date(), o.label());
        } else {
            claim.snapshotLink(null, null, null, null);
        }
    }

    // ------------------------------------------------------------------ delete, withdraw

    @Transactional
    public void delete(ExpenseCaller caller, UUID claimId) {
        ExpenseClaim claim = ownLocked(caller, claimId);
        if (claim.getStatus() != ClaimStatus.DRAFT) {
            throw new ConflictException("Only a Draft can be deleted; this claim is " + claim.getStatus().label().toLowerCase() + ".");
        }
        for (ExpenseBill bill : bills.findByClaimIdOrderByAddedAt(claimId)) {
            if (bill.isLive()) {
                files.remove(caller.userId(), bill.getFileId(), "Draft claim deleted");
            }
            bills.delete(bill);
        }
        recorder.historyOnly(claimId, caller.userId(), "WITHDRAWN", "Draft deleted", null);
        recorder.field(caller.userId(), claimId, "deleted", claim.getAmount(), null);
        claims.delete(claim);
    }

    @Transactional
    public void withdraw(ExpenseCaller caller, UUID claimId, Long version) {
        ExpenseClaim claim = ownLocked(caller, claimId);
        if (claim.getStatus() != ClaimStatus.SUBMITTED) {
            throw new ConflictException(claim.getStatus() == ClaimStatus.DRAFT
                    ? "This claim is already a Draft."
                    : "A claim can be withdrawn only before a decision; this one is "
                            + claim.getStatus().label().toLowerCase() + ".");
        }
        if (version != null) {
            StaleVersion.check(ExpenseClaim.class, claimId, claim.getVersion(), version);
        }
        claim.withdraw(clock.instant());
        claims.saveAndFlush(claim);
        recorder.historyOnly(claimId, caller.userId(), "WITHDRAWN", null, null);
        recorder.field(caller.userId(), claimId, "status", ClaimStatus.SUBMITTED, ClaimStatus.DRAFT);
    }

    // ------------------------------------------------------------------ submit

    @Transactional
    public void submit(ExpenseCaller caller, UUID claimId, Long version, boolean confirmDuplicate) {
        ExpenseClaim claim = ownLocked(caller, claimId);
        if (!claim.getStatus().editable()) {
            throw new ConflictException("Only a Draft or Rejected claim can be submitted; this one is "
                    + claim.getStatus().label().toLowerCase() + ".");
        }
        StaleVersion.check(ExpenseClaim.class, claimId, claim.getVersion(), version);
        doSubmit(caller, claim, confirmDuplicate);
    }

    /** The submission rules of FR-005 and the duplicate check of FR-008, then the status change and the notice. */
    private void doSubmit(ExpenseCaller caller, ExpenseClaim claim, boolean confirmDuplicate) {
        ExpenseInput.checkDateWindow(claim.getExpenseDate(), businessDate.today());
        if (claim.getCategory() == ExpenseCategory.OTHER && claim.getReason() == null) {
            throw new InvalidInputException("A reason is required for the category Other (reason required).");
        }
        if (claim.getCategory() == ExpenseCategory.TRAINING_STAY && claim.getLinkRef() == null) {
            throw new InvalidInputException("A Training Stay claim must be linked to an induction batch (batch required).");
        }
        long liveBills = bills.countByClaimIdAndRemovedAtIsNull(claim.getId());
        if (liveBills == 0 && claim.getNoBillReason() == null) {
            throw new InvalidInputException("Attach a bill, or give the reason there is no bill (bill or no-bill reason required).");
        }
        List<ExpenseClaim> duplicates = claims.duplicatesOf(
                claim.getClaimantId(), claim.getExpenseDate(), claim.getAmount(), claim.getCategory(), claim.getId());
        if (!duplicates.isEmpty()) {
            if (!confirmDuplicate) {
                throw new DuplicateClaimException(duplicates.stream().map(ExpenseClaim::getId).toList());
            }
            recorder.historyOnly(
                    claim.getId(),
                    caller.userId(),
                    "DUPLICATE_CONFIRMED",
                    null,
                    duplicates.stream().map(c -> c.getId().toString()).collect(Collectors.joining(",")));
        }
        boolean resubmitted = claim.getStatus() == ClaimStatus.REJECTED;
        ClaimStatus before = claim.getStatus();
        claim.submit(clock.instant());
        claims.saveAndFlush(claim);
        recorder.event(claim.getId(), caller.userId(), resubmitted ? "RESUBMITTED" : "SUBMITTED", null, null);
        recorder.field(caller.userId(), claim.getId(), "status", before, ClaimStatus.SUBMITTED);
        String name = users.findById(claim.getClaimantId()).map(AppUser::getDisplayName).orElse("A colleague");
        events.publishEvent(new ExpenseSubmitted(claim.getId(), claim.getClaimantId(), name, resubmitted));
    }

    // ------------------------------------------------------------------ helpers

    /** The caller own claim, locked; anyone else (or no such claim) sees "not found". */
    private ExpenseClaim ownLocked(ExpenseCaller caller, UUID claimId) {
        return claims.lockById(claimId)
                .filter(c -> c.getClaimantId().equals(caller.userId()))
                .orElseThrow(() -> new NotFoundException("Claim not found."));
    }
}
