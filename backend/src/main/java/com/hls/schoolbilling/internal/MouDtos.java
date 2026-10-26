package com.hls.schoolbilling.internal;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Request and response shapes of the MoU documents and approval API (spec 031 contracts/mou-approval-api.md). */
public final class MouDtos {

    private MouDtos() {}

    public record PersonRef(UUID id, String name) {}

    public record RemovedInfo(PersonRef by, Instant at, String reason) {}

    public record DocumentDto(
            UUID id,
            int versionNo,
            String name,
            String contentType,
            long sizeBytes,
            PersonRef addedBy,
            Instant addedAt,
            boolean current,
            RemovedInfo removed) {}

    public record DocumentSummary(String state, DocumentDto current) {}

    public record HistoryDto(
            Instant at,
            String action,
            int round,
            PersonRef actor,
            String actorRole,
            String note,
            String detail,
            Integer documentVersion) {}

    /** The approval without documents and history, carried on each contract of the School page. */
    public record ApprovalSummaryDto(
            UUID contractId,
            String state,
            int round,
            PersonRef submittedBy,
            Instant submittedAt,
            PersonRef decidedBy,
            Instant decidedAt,
            String decisionReason,
            boolean selfDecided,
            DocumentSummary document,
            long version,
            List<String> actions) {}

    /** The full approval object: summary plus every document version and the history, oldest first. */
    public record ApprovalDto(
            UUID contractId,
            String state,
            int round,
            PersonRef submittedBy,
            Instant submittedAt,
            PersonRef decidedBy,
            Instant decidedAt,
            String decisionReason,
            boolean selfDecided,
            DocumentSummary document,
            List<DocumentDto> documents,
            List<HistoryDto> history,
            long version,
            List<String> actions) {}

    public record PendingRow(
            UUID contractId,
            UUID schoolId,
            String schoolName,
            String zoneManagerName,
            int round,
            PersonRef submittedBy,
            Instant submittedAt,
            String documentState,
            Integer teacherCount,
            String salaryMode,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal rate,
            LocalDate startsOn,
            LocalDate endsOn,
            long approvalVersion) {}

    public record VersionRequest(Long version) {}

    public record DecisionRequest(Long version, String reason) {}

    public record RemoveRequest(String reason) {}

    public record PositionSalary(Integer number, BigDecimal salary) {}

    /**
     * The fields an approver may correct while a MoU is pending. The last four are never editable and are declared only
     * so that sending one is refused with a clear message instead of being ignored.
     */
    public record AmendRequest(
            Long version,
            BigDecimal rate,
            List<PositionSalary> positions,
            LocalDate signedOn,
            LocalDate startsOn,
            LocalDate endsOn,
            Object teacherCount,
            Object salaryMode,
            Object signatories,
            Object schoolId) {}

    /** What the terms edit answers: the approval and the contract as they now stand. */
    public record AmendResponse(ApprovalDto approval, ContractDtos.ContractDto contract) {}
}
