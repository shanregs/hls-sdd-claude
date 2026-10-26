package com.hls.schoolbilling.internal;

import com.hls.designation.api.BusinessDate;
import com.hls.school.api.ChangeRecorder;
import com.hls.school.api.ConflictException;
import com.hls.school.api.InvalidInputException;
import com.hls.school.api.NotFoundException;
import com.hls.schoolbilling.internal.ReceivablesDtos.AllocationBody;
import com.hls.schoolbilling.internal.ReceivablesDtos.AllocationView;
import com.hls.schoolbilling.internal.ReceivablesDtos.BillingCaller;
import com.hls.schoolbilling.internal.ReceivablesDtos.CollectionBody;
import com.hls.schoolbilling.internal.ReceivablesDtos.CollectionView;
import com.hls.schoolbilling.internal.ReceivablesStore.DueRecord;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Collections received from a School and their allocation to monthly dues (spec 022 US3, research R5 to R7). Collections and
 * allocations are insert-only; a correction is a reversing entry. The School's advisory lock serialises every write so two
 * users cannot allocate the same money or the same balance twice.
 */
@Service
public class CollectionService {

    static final String ENTITY = "SCHOOL_COLLECTION";
    private static final Set<String> MODES = Set.of("BANK", "CASH", "CHEQUE", "UPI");

    /** Thrown for a likely duplicate; the controller turns it into a 409 with a warning the user can confirm past. */
    public static class LikelyDuplicateException extends ConflictException {
        public LikelyDuplicateException() {
            super("A collection with the same School, amount, date, mode and reference already exists.");
        }
    }

    private final ReceivablesStore store;
    private final BillingScope scope;
    private final OverdueFollowUps followUps;
    private final ChangeRecorder changes;
    private final BusinessDate businessDate;
    private final Clock clock;

    CollectionService(
            ReceivablesStore store,
            BillingScope scope,
            OverdueFollowUps followUps,
            ChangeRecorder changes,
            BusinessDate businessDate,
            Clock clock) {
        this.store = store;
        this.scope = scope;
        this.followUps = followUps;
        this.changes = changes;
        this.businessDate = businessDate;
        this.clock = clock;
    }

    @Transactional
    public CollectionView record(BillingCaller caller, CollectionBody body) {
        if (body == null || body.schoolId() == null) {
            throw new InvalidInputException("The School is required.");
        }
        scope.requireSchool(caller, body.schoolId());
        BigDecimal amount = DueAdjustmentService.money(body.amount());
        if (body.receivedOn() == null) {
            throw new InvalidInputException("The date received is required.");
        }
        if (body.receivedOn().isAfter(businessDate.today())) {
            throw new InvalidInputException("The date received cannot be in the future.");
        }
        String mode = body.mode() == null ? "" : body.mode().trim().toUpperCase();
        if (!MODES.contains(mode)) {
            throw new InvalidInputException("The mode must be Bank, Cash, Cheque or UPI.");
        }
        String reference = clean(body.reference(), 80, "The reference");
        String receiver = clean(body.receiver(), 120, "The receiver");
        String comment = clean(body.comment(), 500, "The comment");
        store.lockSchool(body.schoolId());
        if (!Boolean.TRUE.equals(body.confirmDuplicate())
                && store.likelyDuplicate(body.schoolId(), amount, body.receivedOn(), mode, reference)) {
            throw new LikelyDuplicateException();
        }
        UUID id = UUID.randomUUID();
        store.insertCollection(id, body.schoolId(), amount, body.receivedOn(), mode, reference, receiver, comment, null, null,
                caller.userId(), clock.instant());
        changes.recordLifecycle(caller.userId(), ENTITY, id, "created",
                amount.toPlainString() + " " + mode + (reference == null ? "" : " " + reference));
        List<AllocationBody> wanted = body.allocations() == null || body.allocations().isEmpty()
                ? oldestFirst(body.schoolId(), amount)
                : body.allocations();
        applyAllocations(caller, id, body.schoolId(), amount, wanted);
        return store.collection(id).orElseThrow();
    }

    @Transactional
    public CollectionView allocate(BillingCaller caller, UUID collectionId, List<AllocationBody> allocations) {
        CollectionView c = store.collection(collectionId).orElseThrow(() -> new NotFoundException("Collection not found."));
        scope.requireSchool(caller, c.schoolId());
        store.lockSchool(c.schoolId());
        c = store.collection(collectionId).orElseThrow();
        if (c.reversesId() != null || c.reversed()) {
            throw new ConflictException("A reversed collection cannot be allocated.");
        }
        List<AllocationBody> wanted = allocations == null || allocations.isEmpty()
                ? oldestFirst(c.schoolId(), c.unallocated())
                : allocations;
        applyAllocations(caller, collectionId, c.schoolId(), c.unallocated(), wanted);
        return store.collection(collectionId).orElseThrow();
    }

    @Transactional
    public CollectionView reverse(BillingCaller caller, UUID collectionId, String rawReason) {
        CollectionView c = store.collection(collectionId).orElseThrow(() -> new NotFoundException("Collection not found."));
        scope.requireSchool(caller, c.schoolId());
        String reason = DueAdjustmentService.reason(rawReason);
        store.lockSchool(c.schoolId());
        c = store.collection(collectionId).orElseThrow();
        if (c.reversesId() != null) {
            throw new ConflictException("A reversal cannot be reversed. Record a new collection instead.");
        }
        if (c.reversed()) {
            throw new ConflictException("This collection is already reversed.");
        }
        UUID id = UUID.randomUUID();
        store.insertCollection(id, c.schoolId(), c.amount(), c.receivedOn(), c.mode(), c.reference(), c.receiver(), c.comment(),
                c.id(), reason, caller.userId(), clock.instant());
        List<UUID> touched = new ArrayList<>();
        for (AllocationView net : store.netAllocationsOfCollection(collectionId)) {
            store.insertAllocation(collectionId, net.dueId(), net.amount().negate(), caller.userId(), clock.instant());
            touched.add(net.dueId());
        }
        changes.recordLifecycle(caller.userId(), ENTITY, id, "reversed", c.id() + ": " + reason);
        // a due that was paid may be open again; its follow-up, if closed, stays closed (history), a new overdue run does not reopen it
        return store.collection(id).orElseThrow();
    }

    /** Validates and inserts the allocations of one collection; the amount available is {@code available}. */
    private void applyAllocations(BillingCaller caller, UUID collectionId, UUID schoolId, BigDecimal available, List<AllocationBody> wanted) {
        Map<UUID, BigDecimal> perDue = new LinkedHashMap<>();
        BigDecimal sum = BigDecimal.ZERO;
        for (AllocationBody a : wanted) {
            if (a == null || a.dueId() == null) {
                throw new InvalidInputException("Each allocation needs a monthly due.");
            }
            BigDecimal amount = DueAdjustmentService.money(a.amount());
            perDue.merge(a.dueId(), amount, BigDecimal::add);
            sum = sum.add(amount);
        }
        if (sum.compareTo(available) > 0) {
            throw new InvalidInputException("The allocations (" + sum.toPlainString() + ") are more than the " + available.toPlainString()
                    + " available.");
        }
        for (Map.Entry<UUID, BigDecimal> e : perDue.entrySet()) {
            DueRecord due = store.due(e.getKey()).orElseThrow(() -> new NotFoundException("Monthly due not found."));
            if (!due.schoolId().equals(schoolId)) {
                throw new NotFoundException("Monthly due not found.");
            }
            if (due.cancelled()) {
                throw new ConflictException("Monthly due " + due.reference() + " is cancelled.");
            }
            if (e.getValue().compareTo(due.balance()) > 0) {
                throw new InvalidInputException("The allocation to " + due.reference() + " is more than its balance of "
                        + due.balance().toPlainString() + ".");
            }
        }
        for (Map.Entry<UUID, BigDecimal> e : perDue.entrySet()) {
            store.insertAllocation(collectionId, e.getKey(), e.getValue(), caller.userId(), clock.instant());
            changes.recordLifecycle(caller.userId(), ENTITY, collectionId, "allocated", e.getKey() + " " + e.getValue().toPlainString());
        }
        for (UUID dueId : perDue.keySet()) {
            followUps.closeIfSettled(dueId, caller.userId());
        }
    }

    private List<AllocationBody> oldestFirst(UUID schoolId, BigDecimal amount) {
        List<AllocationBody> out = new ArrayList<>();
        BigDecimal left = amount;
        for (DueRecord due : store.openDues(schoolId)) {
            if (left.signum() <= 0) {
                break;
            }
            BigDecimal take = due.balance().min(left);
            out.add(new AllocationBody(due.id(), take));
            left = left.subtract(take);
        }
        return out;
    }

    private static String clean(String value, int max, String what) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String v = value.trim();
        if (v.length() > max) {
            throw new InvalidInputException(what + " must be at most " + max + " characters.");
        }
        return v;
    }
}
