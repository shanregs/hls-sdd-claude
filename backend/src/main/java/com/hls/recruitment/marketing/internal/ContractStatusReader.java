package com.hls.recruitment.marketing.internal;

import com.hls.schoolbilling.api.ContractView;
import com.hls.schoolbilling.api.Occupancy;
import com.hls.schoolbilling.api.PositionView;
import com.hls.schoolbilling.api.SchoolContracts;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Reads the contract of a won prospect's School from spec 012 and sets it beside the proposal. Nothing is copied or
 * stored: the contract lives only in spec 012. With spec 012 absent the status is "not available" and no hand-off is
 * offered (the module works without it).
 */
@Component
class ContractStatusReader {

    static final String NOT_AVAILABLE = "NOT_AVAILABLE";
    static final String NOT_WON = "NOT_WON";
    static final String NOT_RECORDED = "NOT_RECORDED";
    static final String MOU = "MOU";
    static final String ACTIVE = "ACTIVE";

    record PositionSide(int number, String title, String salary) {}

    record ContractSide(
            LocalDate startsOn,
            LocalDate endsOn,
            Integer teacherCount,
            String salaryMode,
            String rate,
            List<PositionSide> positions,
            int filled,
            int vacant) {}

    record Status(
            boolean available,
            String status,
            ContractSide contract,
            ProposalService.ProposalDto proposal,
            List<String> differences) {}

    private final ObjectProvider<SchoolContracts> contracts;
    private final ProposalService proposals;
    private final Clock clock;

    ContractStatusReader(ObjectProvider<SchoolContracts> contracts, ProposalService proposals, Clock clock) {
        this.contracts = contracts;
        this.proposals = proposals;
        this.clock = clock;
    }

    Status of(Prospect prospect) {
        Optional<ProposalService.ProposalDto> proposal = proposals.latest(prospect.getId());
        SchoolContracts source = contracts.getIfAvailable();
        if (source == null) {
            return new Status(false, NOT_AVAILABLE, null, proposal.orElse(null), List.of());
        }
        if (!prospect.isWon()) {
            return new Status(true, NOT_WON, null, proposal.orElse(null), List.of());
        }
        if (prospect.getSchoolId() == null) {
            return new Status(true, NOT_RECORDED, null, proposal.orElse(null), List.of());
        }
        LocalDate today = LocalDate.now(clock);
        ContractView contract = source.contractOf(prospect.getSchoolId(), today)
                .or(() -> source.contractsOverlapping(prospect.getSchoolId(), today, today.plusYears(5)).stream().findFirst())
                .filter(c -> "ACTIVE".equals(c.state()))
                .orElse(null);
        if (contract == null) {
            return new Status(true, NOT_RECORDED, null, proposal.orElse(null), List.of());
        }
        Occupancy occupancy = source.occupancyOf(prospect.getSchoolId(), today);
        ContractSide side = new ContractSide(
                contract.startsOn(),
                contract.endsOn(),
                contract.teacherCount(),
                contract.salaryMode(),
                contract.rate() == null ? null : contract.rate().toPlainString(),
                contract.positions().stream().map(p -> new PositionSide(p.number(), p.title(), p.salary().toPlainString())).toList(),
                occupancy.filled(),
                occupancy.vacant());
        return new Status(
                true, occupancy.filled() > 0 ? ACTIVE : MOU, side, proposal.orElse(null), differences(proposal.orElse(null), contract));
    }

    /** What the signed MoU changed from the proposal, in plain words (the MoU may legitimately differ). */
    private static List<String> differences(ProposalService.ProposalDto proposal, ContractView contract) {
        List<String> out = new ArrayList<>();
        if (proposal == null) {
            return out;
        }
        if (contract.teacherCount() != null && contract.teacherCount() != proposal.teacherCount()) {
            out.add("Teachers: proposal " + proposal.teacherCount() + ", MoU " + contract.teacherCount());
        }
        if (contract.salaryMode() != null && !contract.salaryMode().equals(proposal.salaryMode())) {
            out.add("Salary mode: proposal " + proposal.salaryMode() + ", MoU " + contract.salaryMode());
        } else if ("SAME_FOR_ALL".equals(proposal.salaryMode()) && contract.rate() != null
                && new BigDecimal(proposal.rate()).compareTo(contract.rate()) != 0) {
            out.add("Salary: proposal " + proposal.rate() + ", MoU " + contract.rate().toPlainString());
        } else if ("PER_TEACHER".equals(proposal.salaryMode())) {
            BigDecimal proposed = proposal.positions().stream().map(p -> new BigDecimal(p.salary())).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal signed = contract.positions().stream().map(PositionView::salary).reduce(BigDecimal.ZERO, BigDecimal::add);
            if (proposed.compareTo(signed) != 0) {
                out.add("Monthly total: proposal " + proposed.toPlainString() + ", MoU " + signed.toPlainString());
            }
        }
        if (contract.startsOn() != null && !contract.startsOn().withDayOfMonth(1).equals(proposal.startMonth())) {
            out.add("Start: proposal " + proposal.startMonth() + ", MoU " + contract.startsOn());
        }
        return out;
    }
}
