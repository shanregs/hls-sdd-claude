package com.hls.schoolbilling.internal;

import com.hls.identity.user.AppUser;
import com.hls.identity.user.AppUserRepository;
import com.hls.identity.user.Role;
import com.hls.school.api.ConflictException;
import com.hls.school.api.InvalidInputException;
import com.hls.school.api.StaleVersion;
import com.hls.schoolbilling.api.MouDecided;
import com.hls.schoolbilling.api.MouSubmitted;
import com.hls.schoolbilling.internal.MouDtos.ApprovalDto;
import java.time.Clock;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Submit, approve and reject a MoU (spec 031 US2). A decision takes a row lock on the approval and checks its version, so
 * exactly one decision is recorded per submission; the loser is told who decided and when. A Director may approve what
 * they submitted themselves (clarified, D5): the history shows the same actor twice and the audit record is flagged.
 */
@Service
public class MouApprovalService {

    private static final int MAX_REASON = 500;
    private static final DateTimeFormatter WHEN =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.of("Asia/Kolkata"));

    private final MouAccess access;
    private final MouApprovalRepository approvals;
    private final MouApprovalEventRepository events;
    private final MouRecorder recorder;
    private final MouApprovalQueries queries;
    private final AppUserRepository users;
    private final ApplicationEventPublisher publisher;
    private final Clock clock;

    MouApprovalService(
            MouAccess access,
            MouApprovalRepository approvals,
            MouApprovalEventRepository events,
            MouRecorder recorder,
            MouApprovalQueries queries,
            AppUserRepository users,
            ApplicationEventPublisher publisher,
            Clock clock) {
        this.access = access;
        this.approvals = approvals;
        this.events = events;
        this.recorder = recorder;
        this.queries = queries;
        this.users = users;
        this.publisher = publisher;
        this.clock = clock;
    }

    /** Sends a DRAFT or REJECTED MoU for approval. No document is needed. */
    @Transactional
    public ApprovalDto submit(UUID actor, Set<Role> roles, UUID contractId, Long version) {
        Contract contract = access.contractInScope(actor, roles, contractId);
        access.requireStaffRole(roles);
        if (contract.getState() == ContractState.CANCELLED) {
            throw new ConflictException("This contract was cancelled.");
        }
        if (contract.isPending()) {
            throw new ConflictException("Record the MoU terms first, then submit it for approval.");
        }
        MouApproval approval = lock(contractId);
        MouApprovalStatus before = approval.getStatus();
        if (before != MouApprovalStatus.DRAFT && before != MouApprovalStatus.REJECTED) {
            throw new ConflictException(before == MouApprovalStatus.PENDING
                    ? "This MoU is already waiting for approval."
                    : "This MoU is already approved.");
        }
        StaleVersion.check(MouApproval.class, approval.getId(), approval.getVersion(), version);
        boolean resubmitted = before == MouApprovalStatus.REJECTED;
        approval.submit(actor, clock.instant());
        approvals.saveAndFlush(approval);
        recorder.event(
                approval,
                resubmitted ? "RESUBMITTED" : "SUBMITTED",
                before,
                MouApprovalStatus.PENDING,
                actor,
                roles,
                null,
                null,
                null);
        recorder.approvalChanged(actor, contractId, "status", before.name(), MouApprovalStatus.PENDING.name());
        recorder.approvalChanged(actor, contractId, "round", approval.getRound() - 1, approval.getRound());
        publisher.publishEvent(new MouSubmitted(contractId, contract.getSchoolId(), actor, resubmitted));
        return queries.build(contract, roles);
    }

    @Transactional
    public ApprovalDto approve(UUID actor, Set<Role> roles, UUID contractId, Long version) {
        return decide(actor, roles, contractId, version, null);
    }

    @Transactional
    public ApprovalDto reject(UUID actor, Set<Role> roles, UUID contractId, Long version, String reason) {
        String cleaned = reason == null ? "" : reason.trim();
        // scope and role answer first so a caller outside the scope never learns anything from the validation
        if (cleaned.isEmpty()) {
            access.contractInScope(actor, roles, contractId);
            access.requireApproverRole(roles);
            throw new InvalidInputException("A reason is required to reject a MoU.");
        }
        if (cleaned.length() > MAX_REASON) {
            access.contractInScope(actor, roles, contractId);
            access.requireApproverRole(roles);
            throw new InvalidInputException("The reason must be at most " + MAX_REASON + " characters.");
        }
        return decide(actor, roles, contractId, version, cleaned);
    }

    private ApprovalDto decide(UUID actor, Set<Role> roles, UUID contractId, Long version, String rejectionReason) {
        Contract contract = access.contractInScope(actor, roles, contractId);
        access.requireApproverRole(roles);
        if (contract.getState() == ContractState.CANCELLED) {
            throw new ConflictException("This MoU was cancelled.");
        }
        MouApproval approval = lock(contractId);
        if (approval.getStatus() != MouApprovalStatus.PENDING) {
            throw new ConflictException(alreadyDecided(approval));
        }
        StaleVersion.check(MouApproval.class, approval.getId(), approval.getVersion(), version);

        boolean rejecting = rejectionReason != null;
        boolean termsAmended = events.findByContractIdOrderBySeqAsc(contractId).stream()
                .anyMatch(e -> "TERMS_AMENDED".equals(e.getAction()) && e.getRound() == approval.getRound());
        MouApprovalStatus before = approval.getStatus();
        if (rejecting) {
            approval.reject(actor, rejectionReason, clock.instant());
        } else {
            approval.approve(actor, clock.instant());
        }
        approvals.saveAndFlush(approval);
        boolean self = approval.isSelfDecided();
        recorder.event(
                approval,
                rejecting ? "REJECTED" : "APPROVED",
                before,
                approval.getStatus(),
                actor,
                roles,
                null,
                rejectionReason,
                self ? "Decided by the person who submitted it" : null);
        recorder.approvalChanged(actor, contractId, "status", before.name(), approval.getStatus().name());
        if (rejecting) {
            recorder.approvalChanged(actor, contractId, "decisionReason", null, rejectionReason);
        }
        if (self) {
            recorder.approvalChanged(actor, contractId, "selfDecided", null, true);
        }
        publisher.publishEvent(new MouDecided(
                contractId,
                contract.getSchoolId(),
                approval.getStatus().name(),
                rejectionReason,
                approval.getSubmittedBy(),
                actor,
                termsAmended));
        return queries.build(contract, roles);
    }

    private MouApproval lock(UUID contractId) {
        return approvals.lockByContractId(contractId)
                .orElseThrow(() -> new ConflictException("This contract has no MoU terms to approve."));
    }

    /** Who decided and when, in the wording of the other approvals (spec 015 DecisionService). */
    private String alreadyDecided(MouApproval approval) {
        MouApprovalStatus status = approval.getStatus();
        if (status == MouApprovalStatus.APPROVED_LEGACY) {
            return "This MoU was approved before the approval step existed.";
        }
        if (status == MouApprovalStatus.DRAFT || approval.getDecidedBy() == null || approval.getDecidedAt() == null) {
            return "This MoU is not waiting for a decision.";
        }
        String who = users.findById(approval.getDecidedBy()).map(AppUser::getDisplayName).orElse("another approver");
        String what = status == MouApprovalStatus.APPROVED ? "approved" : "rejected";
        return "This MoU was already decided: " + what + " by " + who + " on " + WHEN.format(approval.getDecidedAt()) + ".";
    }
}
