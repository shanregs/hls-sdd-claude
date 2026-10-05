package com.hls.recruitment.marketing.internal;

import com.hls.identity.user.Role;
import com.hls.school.api.ChangeRecorder;
import com.hls.school.api.InvalidInputException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The proposed MoU terms of a prospect, kept as revisions that never change: a new revision replaces the earlier one,
 * which stays visible. Always labelled "Proposal (not a contract)": the signed contract exists only in spec 012.
 */
@Service
public class ProposalService {

    public static final String LABEL = "Proposal (not a contract)";

    public record PositionInput(String title, String salary) {}

    public record ProposalRequest(
            Integer teacherCount, String startMonth, String salaryMode, String rate, List<PositionInput> positions, String notes) {}

    public record PositionDto(int number, String title, String salary) {}

    public record ProposalDto(
            UUID id,
            int revision,
            String label,
            int teacherCount,
            LocalDate startMonth,
            String salaryMode,
            String rate,
            List<PositionDto> positions,
            String monthlyTotal,
            String notes,
            ProspectService.PersonRef createdBy,
            java.time.Instant createdAt) {}

    private final ProposalRevisionRepository revisions;
    private final ProposalPositionRepository positions;
    private final ProspectService prospects;
    private final ChangeRecorder changes;
    private final Clock clock;

    public ProposalService(
            ProposalRevisionRepository revisions,
            ProposalPositionRepository positions,
            ProspectService prospects,
            ChangeRecorder changes,
            Clock clock) {
        this.revisions = revisions;
        this.positions = positions;
        this.prospects = prospects;
        this.changes = changes;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<ProposalDto> list(UUID userId, Set<Role> roles, UUID prospectId) {
        prospects.visible(userId, roles, prospectId);
        return dtosOf(revisions.findByProspectIdOrderByRevisionDesc(prospectId));
    }

    @Transactional(readOnly = true)
    public Optional<ProposalDto> latest(UUID prospectId) {
        return dtosOf(revisions.findByProspectIdOrderByRevisionDesc(prospectId)).stream().findFirst();
    }

    @Transactional
    public ProposalDto create(UUID actor, Set<Role> roles, UUID prospectId, ProposalRequest request) {
        prospects.visible(actor, roles, prospectId);
        int count = request.teacherCount() == null ? 0 : request.teacherCount();
        if (count < 1 || count > 500) {
            throw new InvalidInputException("The number of Teachers must be between 1 and 500.");
        }
        LocalDate start = startOf(request.startMonth());
        String mode = request.salaryMode() == null ? "" : request.salaryMode().trim().toUpperCase();
        BigDecimal rate = null;
        List<BigDecimal> amounts = new ArrayList<>();
        List<String> titles = new ArrayList<>();
        switch (mode) {
            case "SAME_FOR_ALL" -> {
                if (request.positions() != null && !request.positions().isEmpty()) {
                    throw new InvalidInputException("A same-salary proposal has no positions.");
                }
                rate = money(request.rate(), "The salary");
            }
            case "PER_TEACHER" -> {
                List<PositionInput> given = request.positions() == null ? List.of() : request.positions();
                if (given.size() != count) {
                    throw new InvalidInputException("A different-salary proposal needs one amount for each of the " + count + " Teachers.");
                }
                for (PositionInput p : given) {
                    amounts.add(money(p.salary(), "Each position's salary"));
                    titles.add(Texts.clean(p.title(), 80, "Position title"));
                }
            }
            default -> throw new InvalidInputException("The salary mode must be SAME_FOR_ALL or PER_TEACHER.");
        }
        int number = revisions.latestNumber(prospectId) + 1;
        ProposalRevision revision;
        try {
            revision = revisions.saveAndFlush(new ProposalRevision(
                    prospectId, number, count, start, mode, rate, Texts.clean(request.notes(), 500, "Notes"), actor, clock.instant()));
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            throw new com.hls.school.api.ConflictException("Someone else saved a revision at the same time. Reload and try again.");
        }
        for (int i = 0; i < amounts.size(); i++) {
            positions.save(new ProposalPosition(revision.getId(), i + 1, titles.get(i), amounts.get(i)));
        }
        positions.flush();
        changes.recordLifecycle(actor, "PROPOSAL", prospectId, "revision " + number, summary(mode, count, rate, amounts));
        return dtosOf(List.of(revision)).get(0);
    }

    private static String summary(String mode, int count, BigDecimal rate, List<BigDecimal> amounts) {
        BigDecimal total = rate != null ? rate.multiply(BigDecimal.valueOf(count)) : amounts.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return count + " Teachers, " + mode + ", monthly total " + total.toPlainString();
    }

    private static LocalDate startOf(String value) {
        if (value == null || value.isBlank()) {
            throw new InvalidInputException("The start month is required.");
        }
        try {
            String v = value.trim();
            return (v.length() == 7 ? YearMonth.parse(v) : YearMonth.from(LocalDate.parse(v))).atDay(1);
        } catch (DateTimeParseException e) {
            throw new InvalidInputException("The start month must look like 2026-11.");
        }
    }

    private static BigDecimal money(String value, String label) {
        BigDecimal amount;
        try {
            amount = new BigDecimal(value == null ? "" : value.trim());
        } catch (NumberFormatException e) {
            throw new InvalidInputException(label + " must be an amount.");
        }
        if (amount.signum() <= 0) {
            throw new InvalidInputException(label + " must be more than zero.");
        }
        if (amount.stripTrailingZeros().scale() > 2) {
            throw new InvalidInputException(label + " can have at most two decimals.");
        }
        if (amount.compareTo(new BigDecimal("9999999999.99")) > 0) {
            throw new InvalidInputException(label + " is too large.");
        }
        return amount.setScale(2, java.math.RoundingMode.UNNECESSARY);
    }

    private List<ProposalDto> dtosOf(List<ProposalRevision> list) {
        if (list.isEmpty()) {
            return List.of();
        }
        Map<UUID, List<ProposalPosition>> byRevision = positions
                .findByRevisionIdInOrderByRevisionIdAscNumberAsc(list.stream().map(ProposalRevision::getId).toList())
                .stream()
                .collect(Collectors.groupingBy(ProposalPosition::getRevisionId));
        Map<UUID, String> names = prospects.namesOf(list.stream().map(ProposalRevision::getCreatedBy).collect(Collectors.toSet()));
        return list.stream()
                .map(r -> {
                    List<ProposalPosition> own = byRevision.getOrDefault(r.getId(), List.of());
                    BigDecimal total = r.getRate() != null
                            ? r.getRate().multiply(BigDecimal.valueOf(r.getTeacherCount()))
                            : own.stream().map(ProposalPosition::getSalary).reduce(BigDecimal.ZERO, BigDecimal::add);
                    return new ProposalDto(
                            r.getId(),
                            r.getRevision(),
                            LABEL,
                            r.getTeacherCount(),
                            r.getStartMonth(),
                            r.getSalaryMode(),
                            r.getRate() == null ? null : r.getRate().toPlainString(),
                            own.stream().map(p -> new PositionDto(p.getNumber(), p.getTitle(), p.getSalary().toPlainString())).toList(),
                            total.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString(),
                            r.getNotes(),
                            new ProspectService.PersonRef(r.getCreatedBy(), names.getOrDefault(r.getCreatedBy(), "Unknown user")),
                            r.getCreatedAt());
                })
                .toList();
    }
}
