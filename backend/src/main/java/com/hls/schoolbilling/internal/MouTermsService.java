package com.hls.schoolbilling.internal;

import com.hls.identity.user.Role;
import com.hls.school.api.ConflictException;
import com.hls.school.api.InvalidInputException;
import com.hls.school.api.StaleVersion;
import com.hls.schoolbilling.internal.MouDtos.AmendRequest;
import com.hls.schoolbilling.internal.MouDtos.AmendResponse;
import com.hls.schoolbilling.internal.MouDtos.PositionSalary;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The approver's correction of commercial terms while a MoU is PENDING (spec 031 FR-012): the rate, the position salaries,
 * the signed and end dates, and the start date only when the chain of MoUs stays gapless and no position was filled
 * before the new date. Teacher count, salary mode, signatories and the School are never editable. The database allows
 * a position salary update only while the approval is PENDING (V100).
 */
@Service
public class MouTermsService {

    private static final LocalDate LONG_AGO = LocalDate.of(1900, 1, 1);

    private final MouAccess access;
    private final ContractRepository contracts;
    private final ContractPositionRepository positions;
    private final ContractAssignmentRepository assignments;
    private final MouApprovalRepository approvals;
    private final MouRecorder recorder;
    private final MouApprovalQueries queries;
    private final ContractService contractService;
    private final Clock clock;

    MouTermsService(
            MouAccess access,
            ContractRepository contracts,
            ContractPositionRepository positions,
            ContractAssignmentRepository assignments,
            MouApprovalRepository approvals,
            MouRecorder recorder,
            MouApprovalQueries queries,
            ContractService contractService,
            Clock clock) {
        this.access = access;
        this.contracts = contracts;
        this.positions = positions;
        this.assignments = assignments;
        this.approvals = approvals;
        this.recorder = recorder;
        this.queries = queries;
        this.contractService = contractService;
        this.clock = clock;
    }

    @Transactional
    public AmendResponse amend(UUID actor, Set<Role> roles, UUID contractId, AmendRequest request) {
        Contract contract = access.contractInScope(actor, roles, contractId);
        access.requireApproverRole(roles);
        if (contract.getState() == ContractState.CANCELLED) {
            throw new ConflictException("This MoU was cancelled.");
        }
        if (request.teacherCount() != null
                || request.salaryMode() != null
                || request.signatories() != null
                || request.schoolId() != null) {
            throw new InvalidInputException(
                    "The number of Teachers, the salary mode, the signatories and the School cannot be changed here."
                            + " Reject the MoU and record a new one.");
        }
        MouApproval approval = approvals.lockByContractId(contractId)
                .orElseThrow(() -> new ConflictException("This contract has no MoU terms to correct."));
        if (approval.getStatus() != MouApprovalStatus.PENDING) {
            throw new ConflictException("Terms can only be corrected while the MoU is waiting for approval.");
        }
        StaleVersion.check(MouApproval.class, approval.getId(), approval.getVersion(), request.version());

        LocalDate today = LocalDate.now(clock);
        List<ContractPosition> rows = positions.findByContractIdOrderByNumber(contractId);
        List<String> detail = new ArrayList<>();
        List<Runnable> audits = new ArrayList<>();

        BigDecimal newRate = contract.getRate();
        if (request.rate() != null) {
            if (contract.getSalaryMode() != SalaryMode.SAME_FOR_ALL) {
                throw new InvalidInputException("This MoU has a salary for each position. Send the position salaries.");
            }
            BigDecimal rate = ContractService.money(request.rate(), "The salary");
            if (rate.compareTo(contract.getRate()) != 0) {
                detail.add("rate: " + contract.getRate() + " -> " + rate);
                BigDecimal old = contract.getRate();
                audits.add(() -> recorder.contractChanged(actor, contractId, "rate", old, rate));
                newRate = rate;
                for (ContractPosition p : rows) {
                    p.correctSalary(rate);
                }
            }
        }
        if (request.positions() != null && !request.positions().isEmpty()) {
            if (contract.getSalaryMode() != SalaryMode.PER_TEACHER) {
                throw new InvalidInputException("This MoU has one salary for all Teachers. Send the rate.");
            }
            Map<Integer, ContractPosition> byNumber =
                    rows.stream().collect(Collectors.toMap(ContractPosition::getNumber, Function.identity()));
            Set<Integer> seen = new HashSet<>();
            for (PositionSalary in : request.positions()) {
                ContractPosition p = in.number() == null ? null : byNumber.get(in.number());
                if (p == null) {
                    throw new InvalidInputException("This MoU has no position " + in.number() + ".");
                }
                if (!seen.add(in.number())) {
                    throw new InvalidInputException("Position " + in.number() + " is listed twice.");
                }
                BigDecimal salary = ContractService.money(in.salary(), "The salary of position " + in.number());
                if (salary.compareTo(p.getSalary()) != 0) {
                    detail.add("position " + in.number() + " salary: " + p.getSalary() + " -> " + salary);
                    BigDecimal old = p.getSalary();
                    String field = "positionSalary." + in.number();
                    audits.add(() -> recorder.contractChanged(actor, contractId, field, old, salary));
                    p.correctSalary(salary);
                }
            }
        }

        LocalDate signedOn = contract.getSignedOn();
        if (request.signedOn() != null && !request.signedOn().equals(signedOn)) {
            if (request.signedOn().isAfter(today)) {
                throw new InvalidInputException("The date signed cannot be in the future.");
            }
            detail.add("signedOn: " + signedOn + " -> " + request.signedOn());
            LocalDate old = signedOn;
            LocalDate next = request.signedOn();
            audits.add(() -> recorder.contractChanged(actor, contractId, "signedOn", old, next));
            signedOn = next;
        }
        LocalDate startsOn = contract.getStartsOn();
        if (request.startsOn() != null && !request.startsOn().equals(startsOn)) {
            checkStartDateChange(contract, rows, request.startsOn());
            detail.add("startsOn: " + startsOn + " -> " + request.startsOn());
            LocalDate old = startsOn;
            LocalDate next = request.startsOn();
            audits.add(() -> recorder.contractChanged(actor, contractId, "startsOn", old, next));
            startsOn = next;
        }
        LocalDate endsOn = contract.getEndsOn();
        if (request.endsOn() != null && !request.endsOn().equals(endsOn)) {
            detail.add("endsOn: " + endsOn + " -> " + request.endsOn());
            LocalDate old = endsOn;
            LocalDate next = request.endsOn();
            audits.add(() -> recorder.contractChanged(actor, contractId, "endsOn", old, next));
            endsOn = next;
        }
        if (detail.isEmpty()) {
            throw new InvalidInputException("Nothing was changed.");
        }
        if (endsOn != null && endsOn.isBefore(startsOn)) {
            throw new InvalidInputException("The end date cannot be before the start date.");
        }

        contract.amendTerms(newRate, signedOn, startsOn, endsOn);
        try {
            contracts.saveAndFlush(contract);
            positions.saveAll(rows);
            positions.flush();
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("These dates overlap another contract of this School.");
        }
        audits.forEach(Runnable::run);
        approval.touch(clock.instant());
        approvals.saveAndFlush(approval);
        recorder.event(
                approval,
                "TERMS_AMENDED",
                null,
                null,
                actor,
                roles,
                null,
                "The approver corrected the terms",
                String.join("\n", detail));
        return new AmendResponse(
                queries.build(contract, roles),
                contractService.viewsOf(List.of(contract), roles).get(0));
    }

    /** The accepted default: no gap or overlap in the chain, and nobody placed before the new start. */
    private void checkStartDateChange(Contract contract, List<ContractPosition> rows, LocalDate newStart) {
        LocalDate dayBefore = contract.getStartsOn().minusDays(1);
        boolean chained = contracts.liveNewestFirst(contract.getSchoolId()).stream()
                .anyMatch(c -> !c.getId().equals(contract.getId())
                        && c.getState() != ContractState.RATE_PENDING
                        && dayBefore.equals(c.getEndsOn()));
        if (chained) {
            throw new ConflictException(
                    "An earlier MoU of this School ends the day before this one starts, so the start date cannot move."
                            + " Reject this MoU and record a new one.");
        }
        List<UUID> positionIds = rows.stream().map(ContractPosition::getId).toList();
        if (!positionIds.isEmpty()
                && !assignments.onPositionsOverlapping(positionIds, LONG_AGO, newStart.minusDays(1)).isEmpty()) {
            throw new ConflictException(
                    "A Teacher was placed before the new start date. Reject this MoU and record a new one.");
        }
    }
}
