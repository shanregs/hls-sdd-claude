package com.hls.payroll.internal;

import com.hls.designation.api.DesignationDirectory.Kind;
import com.hls.payroll.api.DayRates;
import com.hls.payroll.internal.PersonDirectory.DesignationRef;
import com.hls.school.api.ChangeRecorder;
import com.hls.school.api.InvalidInputException;
import com.hls.school.api.NotFoundException;
import java.math.BigDecimal;
import com.hls.designation.api.BusinessDate;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Daily rates by designation for Substitute Teachers (spec 017b FR-005, FR-006), kept like the monthly designation
 * salaries of 013a: append-only rows, the latest effective on or before a date wins. Also answers {@link DayRates}.
 */
@Service
public class DayRateService implements DayRates {

    static final String ENTITY = "DESIGNATION_DAY_RATE";
    static final BigDecimal MAX_AMOUNT = new BigDecimal("99999999.99");

    public record RateRow(
            UUID id, String amount, LocalDate effectiveOn, String note, UUID recordedBy, Instant recordedAt) {}

    public record ListEntry(
            UUID designationId, String name, boolean retired, String currentAmount, LocalDate currentEffectiveOn) {}

    private final DesignationDayRateRepository rates;
    private final PersonDirectory people;
    private final ChangeRecorder changes;
    private final Clock clock;
    private final BusinessDate businessDate;

    DayRateService(DesignationDayRateRepository rates, PersonDirectory people, ChangeRecorder changes, Clock clock,
            BusinessDate businessDate) {
        this.rates = rates;
        this.people = people;
        this.changes = changes;
        this.clock = clock;
        this.businessDate = businessDate;
    }

    // ------------------------------------------------------------------ reads

    /** Every Teacher designation with the day rate in effect today; a fixed number of queries. */
    @Transactional(readOnly = true)
    public List<ListEntry> list(boolean includeRetired) {
        LocalDate today = businessDate.today();
        List<DesignationRef> designations = people.designations(Kind.TEACHER, includeRetired);
        Map<UUID, RateOn> inEffect =
                ratesOn(designations.stream().map(DesignationRef::id).toList(), today);
        return designations.stream()
                .map(d -> {
                    RateOn rate = inEffect.get(d.id());
                    return rate instanceof Found f
                            ? new ListEntry(d.id(), d.name(), d.retired(), money(f.perDay()), f.effectiveOn())
                            : new ListEntry(d.id(), d.name(), d.retired(), null, null);
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public List<RateRow> history(UUID designationId) {
        people.designation(designationId).orElseThrow(() -> new NotFoundException("Designation not found."));
        return SalaryResolver.newestFirst(rates.findByDesignationIdOrderByEffectiveOnDescRecordedAtDesc(designationId))
                .stream()
                .map(DayRateService::row)
                .toList();
    }

    // ----------------------------------------------------------------- command

    /** Records a daily rate from an effective date. Any date is allowed so past work can be priced. */
    @Transactional
    public RateRow record(UUID actor, UUID designationId, BigDecimal amount, LocalDate effectiveOn, String note) {
        BigDecimal cleanAmount = amount(amount);
        if (effectiveOn == null) {
            throw new InvalidInputException("The effective date is required.");
        }
        String cleanNote = PayrollInput.note(note);
        people.designation(designationId).orElseThrow(() -> new NotFoundException("Designation not found."));
        BigDecimal prior = rateOn(designationId, effectiveOn) instanceof Found f ? f.perDay() : null;
        DesignationDayRate saved = rates.saveAndFlush(
                new DesignationDayRate(designationId, cleanAmount, effectiveOn, cleanNote, actor, clock.instant()));
        changes.record(actor, ENTITY, designationId, "amount", prior == null ? null : money(prior), money(cleanAmount));
        return row(saved);
    }

    // -------------------------------------------------------------- DayRates

    @Override
    @Transactional(readOnly = true)
    public RateOn rateOn(UUID designationId, LocalDate date) {
        if (designationId == null) {
            return new None("No designation is recorded.");
        }
        return ratesOn(List.of(designationId), date).get(designationId);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, RateOn> ratesOn(Collection<UUID> designationIds, LocalDate date) {
        Map<UUID, RateOn> result = new HashMap<>();
        if (designationIds.isEmpty()) {
            return result;
        }
        Map<UUID, List<DesignationDayRate>> byDesignation = rates.findCandidates(designationIds, date).stream()
                .collect(Collectors.groupingBy(DesignationDayRate::getDesignationId));
        for (UUID id : designationIds) {
            RateOn rate = SalaryResolver.inEffect(byDesignation.getOrDefault(id, List.of()), date)
                    .<RateOn>map(r -> new Found(r.getAmount(), r.effectiveOn()))
                    .orElseGet(() -> new None("No day rate is recorded for this designation on " + date + "."));
            result.put(id, rate);
        }
        return result;
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
            throw new InvalidInputException("The amount is too large.");
        }
        return raw.setScale(2);
    }

    static String money(BigDecimal amount) {
        return amount.setScale(2).toPlainString();
    }

    private static RateRow row(DesignationDayRate r) {
        return new RateRow(
                r.getId(), money(r.getAmount()), r.effectiveOn(), r.getNote(), r.getRecordedBy(), r.recordedAt());
    }
}
