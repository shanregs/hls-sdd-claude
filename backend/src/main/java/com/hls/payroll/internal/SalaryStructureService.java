package com.hls.payroll.internal;

import com.hls.attendance.api.WorkingCalendar;
import com.hls.designation.api.DesignationDirectory.Kind;
import com.hls.identity.user.AppUser;
import com.hls.identity.user.AppUserRepository;
import com.hls.payroll.internal.MissingSalaryReport.Missing;
import com.hls.payroll.internal.PersonDirectory.DesignationRef;
import com.hls.school.api.ChangeRecorder;
import com.hls.school.api.InvalidInputException;
import com.hls.school.api.NotFoundException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The salary of each designation, kept as append-only dated rows (spec 013a US1, US4). A new salary is a new row; the
 * earlier rows are never touched. The salary in effect on a date is the latest row on or before it.
 */
@Service
public class SalaryStructureService {

    static final String ENTITY = "DESIGNATION_SALARY";
    static final String MAX_AMOUNT_TEXT = "9,999,999.99";
    static final BigDecimal MAX_AMOUNT = new BigDecimal("9999999.99");
    static final String DEFAULT_NOTE =
            "A suggested default only: it is offered when a person's salary is entered, and nobody is paid it.";

    public record Actor(UUID id, String name) {}

    /** The salary in effect: the amount as a string with two decimals, and the date it took effect. */
    public record InEffect(String amount, LocalDate effectiveOn) {}

    public record MissingSalary(boolean flag, long people, String note) {}

    /** One row of the designation list: the suggested default in effect, and who holds it without a salary of their own. */
    public record ListEntry(
            UUID designationId,
            String name,
            Kind kind,
            boolean retired,
            InEffect inEffect,
            MissingSalary missingSalary,
            String defaultNote) {}

    public record SalaryRow(
            UUID id,
            UUID designationId,
            String amount,
            LocalDate effectiveOn,
            String note,
            Actor recordedBy,
            Instant recordedAt) {}

    public record Detail(
            UUID designationId,
            String name,
            Kind kind,
            boolean retired,
            InEffect inEffect,
            MissingSalary missingSalary,
            String defaultNote,
            List<SalaryRow> history) {}

    private final DesignationSalaryRepository salaries;
    private final PersonDirectory people;
    private final MissingSalaryReport missing;
    private final ChangeRecorder changes;
    private final WorkingCalendar calendar;
    private final AppUserRepository users;
    private final Clock clock;

    SalaryStructureService(
            DesignationSalaryRepository salaries,
            PersonDirectory people,
            MissingSalaryReport missing,
            ChangeRecorder changes,
            WorkingCalendar calendar,
            AppUserRepository users,
            Clock clock) {
        this.salaries = salaries;
        this.people = people;
        this.missing = missing;
        this.changes = changes;
        this.calendar = calendar;
        this.users = users;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ reads

    /** Every designation with the salary in effect today; a fixed number of queries however many there are. */
    @Transactional(readOnly = true)
    public List<ListEntry> list(Kind kind, boolean includeRetired) {
        LocalDate today = calendar.businessToday();
        List<DesignationRef> designations = people.designations(kind, includeRetired);
        Map<UUID, DesignationSalary> inEffect = inEffectToday(designations, today);
        Map<UUID, Missing> gaps = missing.compute(designations, today);
        return designations.stream()
                .map(d -> entry(d, inEffect.get(d.id()), gaps.get(d.id())))
                .toList();
    }

    @Transactional(readOnly = true)
    public Detail detail(UUID designationId) {
        DesignationRef designation = people.designation(designationId)
                .orElseThrow(() -> new NotFoundException("Designation not found."));
        LocalDate today = calendar.businessToday();
        List<DesignationSalary> rows =
                salaries.findByDesignationIdOrderByEffectiveOnDescRecordedAtDesc(designationId);
        DesignationSalary current = SalaryResolver.inEffect(rows, today).orElse(null);
        Missing gap = missing.compute(List.of(designation), today).get(designationId);
        Map<UUID, String> names = names(rows);
        return new Detail(
                designation.id(),
                designation.name(),
                designation.kind(),
                designation.retired(),
                inEffect(current),
                gap(gap),
                DEFAULT_NOTE,
                SalaryResolver.newestFirst(rows).stream().map(r -> row(r, names)).toList());
    }

    // ----------------------------------------------------------------- commands

    /**
     * Records a salary from an effective date. Refused with the problem named when the amount is missing, zero,
     * negative, has more than two decimals or exceeds the maximum, when the date is missing or before the first day of
     * the current month, or when the note is over 500 characters. A retired designation is accepted.
     */
    @Transactional
    public SalaryRow record(UUID actor, UUID designationId, BigDecimal amount, LocalDate effectiveOn, String note) {
        BigDecimal cleanAmount = amount(amount);
        LocalDate date = PayrollInput.effectiveDate(effectiveOn, calendar.businessToday());
        String cleanNote = PayrollInput.note(note);
        people.designation(designationId).orElseThrow(() -> new NotFoundException("Designation not found."));

        // the amount this date had before the new row, for the audit entry
        BigDecimal prior = salaries
                .findFirstByDesignationIdAndEffectiveOnLessThanEqualOrderByEffectiveOnDescRecordedAtDesc(
                        designationId, date)
                .map(DesignationSalary::getAmount)
                .orElse(null);
        DesignationSalary saved = salaries.saveAndFlush(
                new DesignationSalary(designationId, cleanAmount, date, cleanNote, actor, clock.instant()));
        if (prior != null && prior.compareTo(cleanAmount) == 0) {
            changes.recordLifecycle(actor, ENTITY, designationId, "amount", "re-recorded " + money(cleanAmount) + " from " + date);
        } else {
            changes.record(actor, ENTITY, designationId, "amount", prior == null ? null : money(prior), money(cleanAmount));
        }
        return row(saved, names(List.of(saved)));
    }

    // ------------------------------------------------------------------ helpers

    static BigDecimal amount(BigDecimal raw) {
        if (raw == null) {
            throw new InvalidInputException("The amount is required.");
        }
        if (raw.signum() <= 0) {
            throw new InvalidInputException("The amount must be more than zero.");
        }
        if (PayrollInput.decimals(raw) > 2) {
            throw new InvalidInputException("The amount can have at most two decimal places.");
        }
        if (raw.compareTo(MAX_AMOUNT) > 0) {
            throw new InvalidInputException("The amount can be at most " + MAX_AMOUNT_TEXT + ".");
        }
        return raw.setScale(2);
    }

    static String money(BigDecimal amount) {
        return amount.setScale(2).toPlainString();
    }

    private Map<UUID, DesignationSalary> inEffectToday(List<DesignationRef> designations, LocalDate today) {
        if (designations.isEmpty()) {
            return Map.of();
        }
        Set<UUID> ids = designations.stream().map(DesignationRef::id).collect(Collectors.toSet());
        Map<UUID, List<DesignationSalary>> byDesignation =
                salaries.findCandidates(ids, today).stream().collect(Collectors.groupingBy(DesignationSalary::getDesignationId));
        Map<UUID, DesignationSalary> result = new HashMap<>();
        byDesignation.forEach((id, rows) -> SalaryResolver.inEffect(rows, today).ifPresent(r -> result.put(id, r)));
        return result;
    }

    private static ListEntry entry(DesignationRef d, DesignationSalary current, Missing gap) {
        return new ListEntry(
                d.id(), d.name(), d.kind(), d.retired(), inEffect(current), gap(gap), DEFAULT_NOTE);
    }

    private static InEffect inEffect(DesignationSalary current) {
        return current == null ? null : new InEffect(money(current.getAmount()), current.effectiveOn());
    }

    private static MissingSalary gap(Missing gap) {
        return gap == null ? new MissingSalary(false, 0, null) : new MissingSalary(gap.flag(), gap.people(), gap.note());
    }

    private SalaryRow row(DesignationSalary r, Map<UUID, String> names) {
        return new SalaryRow(
                r.getId(),
                r.getDesignationId(),
                money(r.getAmount()),
                r.effectiveOn(),
                r.getNote(),
                new Actor(r.getRecordedBy(), names.getOrDefault(r.getRecordedBy(), "Unknown user")),
                r.recordedAt());
    }

    private Map<UUID, String> names(List<DesignationSalary> rows) {
        Set<UUID> ids = new HashSet<>();
        rows.forEach(r -> ids.add(Objects.requireNonNull(r.getRecordedBy())));
        return ids.isEmpty()
                ? Map.of()
                : users.findAllById(ids).stream().collect(Collectors.toMap(AppUser::getId, AppUser::getDisplayName));
    }
}
