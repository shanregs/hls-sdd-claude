package com.hls.schoolbilling.internal;

import com.hls.files.api.FileStore;
import com.hls.files.api.FileStore.FileContent;
import com.hls.files.api.FileStore.FileRef;
import com.hls.identity.user.Role;
import com.hls.school.api.ConflictException;
import com.hls.school.api.InvalidInputException;
import com.hls.school.api.NotFoundException;
import com.hls.schoolbilling.api.MouSubmitted;
import com.hls.schoolbilling.internal.MouDtos.ApprovalDto;
import java.time.Clock;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Upload, replace, remove and download of the versions of a MoU signed document (spec 031 US1). Bytes live in the shared
 * {@code files} module; this service keeps the versions, the reasons and the history. A new version on an APPROVED MoU
 * resets it to PENDING in the same transaction; removing a version never changes the status.
 */
@Service
public class MouDocumentService {

    private static final String OWNER_TYPE = "MOU_DOCUMENT";
    private static final int MAX_REASON = 300;

    private final MouAccess access;
    private final MouApprovalRepository approvals;
    private final MouDocumentRepository documents;
    private final MouRecorder recorder;
    private final MouApprovalQueries queries;
    private final FileStore files;
    private final ApplicationEventPublisher publisher;
    private final Clock clock;

    MouDocumentService(
            MouAccess access,
            MouApprovalRepository approvals,
            MouDocumentRepository documents,
            MouRecorder recorder,
            MouApprovalQueries queries,
            FileStore files,
            ApplicationEventPublisher publisher,
            Clock clock) {
        this.access = access;
        this.approvals = approvals;
        this.documents = documents;
        this.recorder = recorder;
        this.queries = queries;
        this.files = files;
        this.publisher = publisher;
        this.clock = clock;
    }

    @Transactional
    public ApprovalDto upload(UUID actor, Set<Role> roles, UUID contractId, String fileName, byte[] bytes) {
        Contract contract = access.contractInScope(actor, roles, contractId);
        access.requireStaffRole(roles);
        if (contract.isPending()) {
            throw new ConflictException("Record the MoU terms first, then attach the signed document.");
        }
        MouDocumentPolicy.check(fileName);
        MouApproval approval = approvals.lockByContractId(contractId)
                .orElseThrow(() -> new ConflictException("This contract has no MoU terms to attach a document to."));

        FileRef stored = files.store(actor, OWNER_TYPE, contractId, fileName, bytes);
        int versionNo = documents.maxVersion(contractId) + 1;
        boolean replaced = documents.findByContractIdOrderByVersionNoDesc(contractId).stream().anyMatch(d -> !d.isRemoved());
        documents.saveAndFlush(new MouDocument(
                contractId,
                versionNo,
                stored.id(),
                stored.name(),
                stored.contentType(),
                stored.sizeBytes(),
                actor,
                clock.instant()));
        String detail = "version " + versionNo + ": " + stored.name();
        recorder.event(
                approval,
                replaced ? "DOCUMENT_REPLACED" : "DOCUMENT_ADDED",
                null,
                null,
                actor,
                roles,
                versionNo,
                stored.name(),
                null);
        recorder.documentChanged(actor, contractId, replaced ? "replaced" : "added", detail);

        if (approval.getStatus() == MouApprovalStatus.APPROVED && contract.getState() != ContractState.CANCELLED) {
            // a replaced document is a new thing to approve: back to pending, the uploader counts as the submitter
            MouApprovalStatus before = approval.getStatus();
            approval.submit(actor, clock.instant());
            approvals.saveAndFlush(approval);
            recorder.event(
                    approval,
                    "RESET_TO_PENDING",
                    before,
                    MouApprovalStatus.PENDING,
                    actor,
                    roles,
                    versionNo,
                    "The signed document was replaced",
                    null);
            recorder.approvalChanged(actor, contractId, "status", before.name(), MouApprovalStatus.PENDING.name());
            recorder.approvalChanged(actor, contractId, "round", approval.getRound() - 1, approval.getRound());
            publisher.publishEvent(new MouSubmitted(contractId, contract.getSchoolId(), actor, true));
        }
        return queries.build(contract, roles);
    }

    @Transactional
    public ApprovalDto remove(UUID actor, Set<Role> roles, UUID contractId, UUID documentId, String reason) {
        Contract contract = access.contractInScope(actor, roles, contractId);
        access.requireStaffRole(roles);
        MouDocument document = documents.findById(documentId)
                .filter(d -> d.getContractId().equals(contractId) && !d.isRemoved())
                .orElseThrow(() -> new NotFoundException("Document not found."));
        String cleaned = reason == null ? "" : reason.trim();
        if (cleaned.isEmpty()) {
            throw new InvalidInputException("A reason is required to remove a document.");
        }
        if (cleaned.length() > MAX_REASON) {
            throw new InvalidInputException("The reason must be at most " + MAX_REASON + " characters.");
        }
        files.remove(actor, document.getFileId(), cleaned);
        document.markRemoved(actor, cleaned, clock.instant());
        documents.saveAndFlush(document);
        MouApproval approval = approvals.findByContractId(contractId).orElse(null);
        if (approval != null) {
            recorder.event(
                    approval, "DOCUMENT_REMOVED", null, null, actor, roles, document.getVersionNo(), cleaned, null);
        }
        recorder.documentChanged(
                actor, contractId, "removed", "version " + document.getVersionNo() + ": " + document.getFileName() + " (" + cleaned + ")");
        return queries.build(contract, roles);
    }

    /** The bytes of a version that is not removed, for a caller who may see the MoU. */
    @Transactional(readOnly = true)
    public FileContent download(UUID actor, Set<Role> roles, UUID contractId, UUID documentId) {
        access.contractInScope(actor, roles, contractId);
        MouDocument document = documents.findById(documentId)
                .filter(d -> d.getContractId().equals(contractId) && !d.isRemoved())
                .orElseThrow(() -> new NotFoundException("Document not found."));
        return files.open(document.getFileId()).orElseThrow(() -> new NotFoundException("Document not found."));
    }
}
