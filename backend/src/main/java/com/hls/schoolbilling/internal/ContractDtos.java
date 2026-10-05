package com.hls.schoolbilling.internal;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Request and response shapes of the School contracts API (spec 012 contracts/school-contracts-api.md). */
public final class ContractDtos {

    private ContractDtos() {}

    /** One position of a "different for each Teacher" contract. */
    public record PositionInput(String title, BigDecimal salary) {}

    public record SchoolSignatoryInput(String name, String designation) {}

    public record HlsSignatoryInput(UUID userId, String designation) {}

    /** The MoU details, as entered on a new contract or recorded on a pending one. */
    public record MouRequest(
            Integer teacherCount,
            SalaryMode salaryMode,
            BigDecimal rate,
            List<PositionInput> positions,
            LocalDate signedOn,
            List<SchoolSignatoryInput> schoolSignatories,
            List<HlsSignatoryInput> hlsSignatories) {}

    /** A new MoU: the details plus its dates. */
    public record NewContractRequest(
            Integer teacherCount,
            SalaryMode salaryMode,
            BigDecimal rate,
            List<PositionInput> positions,
            LocalDate signedOn,
            List<SchoolSignatoryInput> schoolSignatories,
            List<HlsSignatoryInput> hlsSignatories,
            LocalDate startsOn,
            LocalDate endsOn) {}

    public record EndRequest(LocalDate endsOn) {}

    /** What the list and the School page call a contract's state. */
    public enum ContractStatus {
        ACTIVE,
        ENDS_SOON,
        MOU_PENDING,
        NONE,
        ENDED,
        CANCELLED
    }

    public record PositionDto(
            UUID id,
            int number,
            String title,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal salary,
            UUID teacherId,
            String teacherName) {}

    public record SignatoryDto(UUID id, String party, String name, String designation, UUID userId) {}

    public record ContractDto(
            UUID id,
            UUID schoolId,
            String state,
            ContractStatus status,
            String salaryMode,
            Integer teacherCount,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal rate,
            LocalDate signedOn,
            LocalDate startsOn,
            LocalDate endsOn,
            long version,
            List<PositionDto> positions,
            List<SignatoryDto> signatories) {}

    /** A Teacher who is at the School but not mapped to a position of the contract in effect. */
    public record UnmappedTeacherDto(UUID teacherId, String teacherName) {}

    public record SchoolContractsDto(
            UUID schoolId,
            String schoolName,
            String zoneManagerName,
            List<ContractDto> contracts,
            List<UnmappedTeacherDto> unmappedTeachers) {}

    /** The HLS signatories on offer for a School: its Zone Manager and the active Directors. */
    public record SignatoryCandidate(UUID userId, String name, String designation) {}

    /** One row of the School Contracts list. */
    public record ContractListRow(
            UUID schoolId,
            String schoolName,
            String zoneManagerName,
            ContractStatus status,
            UUID contractId,
            LocalDate startsOn,
            LocalDate endsOn,
            Integer teacherCount,
            int filled,
            int vacant,
            int unmapped,
            String salaryMode,
            LocalDate signedOn) {}
}
