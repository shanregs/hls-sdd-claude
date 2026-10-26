package com.hls.schoolbilling.internal;

import com.hls.schoolbilling.api.BillingCalendar;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Works out what a School owes for a month from its ACTIVE assignments (spec 022 FR-001 to FR-003, research R1 and R2).
 * A Teacher assigned to a position for the whole month is billed the position salary exactly; a part month is
 * {@code salary x working days at the School / the School's working days in the month}, rounded half up per line.
 * Absences, leave and substitutes never enter. Reads this module's own tables; other modules only through their api.
 */
@Component
public class DueCalculator {

    public static final String NO_POSITION = "NO_POSITION";
    public static final String MOU_PENDING = "MOU_PENDING";
    public static final String NO_ACTIVE_CONTRACT = "NO_ACTIVE_CONTRACT";

    /** One billed span of a Teacher at a position. */
    public record Line(
            UUID teacherId,
            UUID positionId,
            int positionNumber,
            UUID contractId,
            BigDecimal salary,
            LocalDate from,
            LocalDate to,
            BigDecimal workedDays,
            BigDecimal monthDays,
            boolean fullMonth,
            BigDecimal amount) {}

    /** A Teacher (or, with a null id, a School) that could not be billed, and why. */
    public record Unbilled(UUID teacherId, String reason) {}

    public record Result(List<Line> lines, List<Unbilled> notBilled) {
        public BigDecimal total() {
            return lines.stream().map(Line::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        }
    }

    private final ContractAssignmentRepository assignments;
    private final ContractPositionRepository positions;
    private final ContractRepository contracts;
    private final BillingCalendar calendar;

    DueCalculator(
            ContractAssignmentRepository assignments,
            ContractPositionRepository positions,
            ContractRepository contracts,
            BillingCalendar calendar) {
        this.assignments = assignments;
        this.positions = positions;
        this.contracts = contracts;
        this.calendar = calendar;
    }

    public Result compute(UUID schoolId, YearMonth month) {
        LocalDate first = month.atDay(1);
        LocalDate last = month.atEndOfMonth();
        List<ContractAssignment> here = assignments.atSchoolOverlapping(schoolId, first, last);
        List<Line> lines = new ArrayList<>();
        List<Unbilled> notBilled = new ArrayList<>();
        if (here.isEmpty()) {
            return new Result(lines, notBilled);
        }
        Set<UUID> positionIds = new HashSet<>();
        here.forEach(a -> {
            if (a.getPositionId() != null) {
                positionIds.add(a.getPositionId());
            }
        });
        Map<UUID, ContractPosition> positionById = positions.findAllById(positionIds).stream()
                .collect(Collectors.toMap(ContractPosition::getId, p -> p));
        Map<UUID, Contract> contractById = new HashMap<>();
        contracts.findAllById(positionById.values().stream().map(ContractPosition::getContractId).distinct().toList())
                .forEach(c -> contractById.put(c.getId(), c));
        // a School whose live contracts in the month are all "MoU pending" (or that has none) is not billed at all
        boolean anyPending = contracts.liveOverlapping(List.of(schoolId), first, last).stream().anyMatch(Contract::isPending);
        BigDecimal monthDays = calendar.monthWorkingDays(schoolId, month);
        Set<UUID> unbilledSeen = new HashSet<>();

        for (ContractAssignment a : here) {
            ContractPosition position = a.getPositionId() == null ? null : positionById.get(a.getPositionId());
            if (position == null) {
                addUnbilled(notBilled, unbilledSeen, a.getTeacherId(), anyPending ? MOU_PENDING : NO_POSITION);
                continue;
            }
            Contract contract = contractById.get(position.getContractId());
            if (contract == null || contract.getState() == ContractState.CANCELLED) {
                addUnbilled(notBilled, unbilledSeen, a.getTeacherId(), NO_ACTIVE_CONTRACT);
                continue;
            }
            if (contract.isPending()) {
                addUnbilled(notBilled, unbilledSeen, a.getTeacherId(), MOU_PENDING);
                continue;
            }
            LocalDate from = max(a.getStartsOn(), first, contract.getStartsOn());
            LocalDate to = min(a.getEndsOn() == null ? last : a.getEndsOn(), last, contract.getEndsOn() == null ? last : contract.getEndsOn());
            if (from.isAfter(to)) {
                addUnbilled(notBilled, unbilledSeen, a.getTeacherId(), NO_ACTIVE_CONTRACT);
                continue;
            }
            boolean full = from.equals(first) && to.equals(last);
            BigDecimal salary = position.getSalary().setScale(2, RoundingMode.HALF_UP);
            BigDecimal worked;
            BigDecimal amount;
            if (full) {
                worked = monthDays;
                amount = salary;
            } else {
                worked = BigDecimal.valueOf(calendar.workingDaysBetween(schoolId, from, to));
                if (worked.compareTo(monthDays) > 0) {
                    worked = monthDays;
                }
                amount = monthDays.signum() == 0
                        ? BigDecimal.ZERO.setScale(2)
                        : salary.multiply(worked).divide(monthDays, 2, RoundingMode.HALF_UP);
            }
            lines.add(new Line(
                    a.getTeacherId(),
                    position.getId(),
                    position.getNumber(),
                    contract.getId(),
                    salary,
                    from,
                    to,
                    worked.setScale(1, RoundingMode.HALF_UP),
                    monthDays.setScale(1, RoundingMode.HALF_UP),
                    full,
                    amount));
        }
        return new Result(lines, notBilled);
    }

    /** The working shown on a line, for the detail screen. */
    public static String working(Line l) {
        if (l.fullMonth()) {
            return "Whole month at " + l.salary().toPlainString();
        }
        return l.salary().toPlainString() + " x " + l.workedDays().toPlainString() + " working days at the School / "
                + l.monthDays().toPlainString() + " working days in the month";
    }

    private static void addUnbilled(List<Unbilled> out, Set<UUID> seen, UUID teacherId, String reason) {
        if (seen.add(teacherId)) {
            out.add(new Unbilled(teacherId, reason));
        }
    }

    private static LocalDate max(LocalDate a, LocalDate b, LocalDate c) {
        LocalDate m = a.isAfter(b) ? a : b;
        return m.isAfter(c) ? m : c;
    }

    private static LocalDate min(LocalDate a, LocalDate b, LocalDate c) {
        LocalDate m = a.isBefore(b) ? a : b;
        return m.isBefore(c) ? m : c;
    }
}
