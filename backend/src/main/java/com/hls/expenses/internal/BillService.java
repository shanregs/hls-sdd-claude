package com.hls.expenses.internal;

import com.hls.files.api.FileStore;
import com.hls.files.api.FileStore.FileContent;
import com.hls.files.api.FileStore.FileRef;
import com.hls.school.api.ConflictException;
import com.hls.school.api.ForbiddenFieldException;
import com.hls.school.api.InvalidInputException;
import com.hls.school.api.NotFoundException;
import java.time.Clock;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bills of a claim, kept in the shared file store (spec 015 FR-006): jpg, png or pdf, at most 10 MB and 5 per claim. A
 * claimant adds bills only while the claim is a Draft or Rejected; an Admin or Director removes one with a reason, and
 * the removal is recorded (the file itself stays in the store).
 */
@Service
public class BillService {

    static final String OWNER_TYPE = "EXPENSE_CLAIM";
    static final int MAX_BILLS = 5;
    private static final Set<String> EXTENSIONS = Set.of("jpg", "jpeg", "png", "pdf");

    private final ExpenseClaimRepository claims;
    private final ExpenseBillRepository bills;
    private final FileStore files;
    private final ClaimRecorder recorder;
    private final Clock clock;

    BillService(ExpenseClaimRepository claims, ExpenseBillRepository bills, FileStore files, ClaimRecorder recorder, Clock clock) {
        this.claims = claims;
        this.bills = bills;
        this.files = files;
        this.recorder = recorder;
        this.clock = clock;
    }

    /** The claimant adds a bill to an own Draft or Rejected claim. */
    @Transactional
    public void add(ExpenseCaller caller, UUID claimId, String originalName, byte[] content) {
        ExpenseClaim claim = claims.lockById(claimId)
                .filter(c -> c.getClaimantId().equals(caller.userId()))
                .orElseThrow(() -> new NotFoundException("Claim not found."));
        if (!claim.getStatus().editable()) {
            throw new ConflictException("Bills can be added only while a claim is a Draft or Rejected.");
        }
        String name = originalName == null ? "" : originalName.trim();
        String extension = name.lastIndexOf('.') < 0 ? "" : name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        if (!EXTENSIONS.contains(extension)) {
            throw new InvalidInputException("A bill must be a jpg, png or pdf file.");
        }
        if (content == null || content.length == 0) {
            throw new InvalidInputException("The file is empty.");
        }
        if (content.length > FileStore.MAX_BYTES) {
            throw new InvalidInputException("The file is too large. A bill can be at most 10 MB.");
        }
        if (bills.countByClaimIdAndRemovedAtIsNull(claimId) >= MAX_BILLS) {
            throw new InvalidInputException("A claim can have at most " + MAX_BILLS + " bills.");
        }
        FileRef file = files.store(caller.userId(), OWNER_TYPE, claimId, name, content);
        ExpenseBill bill = bills.save(new ExpenseBill(
                claimId, file.id(), file.name(), file.contentType(), file.sizeBytes(), caller.userId(), clock.instant()));
        recorder.event(claimId, caller.userId(), "BILL_ADDED", null, file.name());
        recorder.field(caller.userId(), claimId, "bill", null, bill.getName());
    }

    /** Opens a bill after the caller proved access to the claim; a removed bill is not available. */
    @Transactional(readOnly = true)
    public FileContent open(UUID claimId, UUID billId) {
        ExpenseBill bill = bills.findById(billId)
                .filter(b -> b.getClaimId().equals(claimId) && b.isLive())
                .orElseThrow(() -> new NotFoundException("Bill not found."));
        return files.open(bill.getFileId()).orElseThrow(() -> new NotFoundException("Bill not found."));
    }

    /** An Admin or Director removes a bill with a reason; the claim status does not change. */
    @Transactional
    public void remove(ExpenseCaller caller, UUID claimId, UUID billId, String reason) {
        ExpenseClaim claim = claims.lockById(claimId).orElseThrow(() -> new NotFoundException("Claim not found."));
        if (claim.getClaimantId().equals(caller.userId())) {
            throw new ForbiddenFieldException("You cannot remove a bill from a claim you raised.");
        }
        if (claim.getStatus() == ClaimStatus.DRAFT) {
            throw new NotFoundException("Claim not found.");
        }
        String why = ExpenseInput.requiredText(reason, ExpenseInput.MAX_REASON, "The reason");
        ExpenseBill bill = bills.findById(billId)
                .filter(b -> b.getClaimId().equals(claimId))
                .orElseThrow(() -> new NotFoundException("Bill not found."));
        if (!bill.isLive()) {
            throw new ConflictException("This bill was already removed.");
        }
        bill.remove(caller.userId(), why, clock.instant());
        files.remove(caller.userId(), bill.getFileId(), why);
        recorder.event(claimId, caller.userId(), "BILL_REMOVED", why, bill.getName());
        recorder.field(caller.userId(), claimId, "bill", bill.getName(), null);
    }
}
