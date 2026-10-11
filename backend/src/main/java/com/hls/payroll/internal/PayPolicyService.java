package com.hls.payroll.internal;

import com.hls.attendance.api.WorkingCalendar;
import com.hls.identity.user.AppUser;
import com.hls.identity.user.AppUserRepository;
import com.hls.payroll.api.PayPolicyView;
import com.hls.payroll.api.PayPolicyView.LopDivisor;
import com.hls.payroll.api.PayPolicyView.PayMonth;
import com.hls.payroll.api.PayPolicyView.RoundingRule;
import com.hls.school.api.ChangeRecorder;
import com.hls.school.api.InvalidInputException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The pay policy: dated, append-only versions (spec 013a US2). A correction is a newer version, never an edit. */
@Service
public class PayPolicyService {

    static final String ENTITY = "PAY_POLICY";

    public record Actor(UUID id, String name) {}

    public record PolicyVersion(
            UUID id,
            LocalDate effectiveOn,
            LopDivisor lopDivisor,
            String halfDayFraction,
            RoundingRule rounding,
            PayMonth payMonth,
            String note,
            Actor recordedBy,
            Instant recordedAt) {}

    public record PolicyResponse(PolicyVersion inEffect, List<PolicyVersion> history) {}

    private final PayPolicyRepository repository;
    private final ChangeRecorder changes;
    private final WorkingCalendar calendar;
    private final AppUserRepository users;
    private final Clock clock;

    PayPolicyService(
            PayPolicyRepository repository,
            ChangeRecorder changes,
            WorkingCalendar calendar,
            AppUserRepository users,
            Clock clock) {
        this.repository = repository;
        this.changes = changes;
        this.calendar = calendar;
        this.users = users;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ reads

    /** The version in effect on a date. A date before the first version still gets that first version. */
    @Transactional(readOnly = true)
    public PayPolicyView policyOn(LocalDate date) {
        return versionOn(date).toView();
    }

    @Transactional(readOnly = true)
    public PolicyResponse policy() {
        List<PayPolicy> all = repository.findAllByOrderByEffectiveOnDescRecordedAtDesc();
        Map<UUID, String> names = names(all);
        PayPolicy today = SalaryResolver.inEffect(all, calendar.businessToday()).orElseGet(() -> oldest(all));
        return new PolicyResponse(
                version(today, names), all.stream().map(p -> version(p, names)).toList());
    }

    // ----------------------------------------------------------------- commands

    @Transactional
    public PolicyVersion record(
            UUID actor,
            LocalDate effectiveOn,
            String lopDivisor,
            BigDecimal halfDayFraction,
            String rounding,
            String payMonth,
            String note) {
        LocalDate date = PayrollInput.effectiveDate(effectiveOn, calendar.businessToday());
        LopDivisor divisor = parse(LopDivisor.class, lopDivisor, "loss-of-pay divisor");
        RoundingRule rule = parse(RoundingRule.class, rounding, "rounding rule");
        PayMonth month = parse(PayMonth.class, payMonth, "pay month");
        BigDecimal fraction = fraction(halfDayFraction);
        String cleanNote = PayrollInput.note(note);

        PayPolicy before = versionOn(date);
        PayPolicy saved = repository.saveAndFlush(
                new PayPolicy(date, divisor, fraction, rule, month, cleanNote, actor, clock.instant()));

        int audited = 0;
        audited += audit(actor, saved, "lopDivisor", before.getLopDivisor(), divisor);
        audited += audit(actor, saved, "halfDayFraction", fixed(before.getHalfDayFraction()), fixed(fraction));
        audited += audit(actor, saved, "rounding", before.getRounding(), rule);
        audited += audit(actor, saved, "payMonth", before.getPayMonth(), month);
        if (audited == 0) {
            // a dated version that changes nothing is still a recorded decision
            changes.recordLifecycle(actor, ENTITY, saved.getId(), "created", "effective " + date + ", no value changed");
        }
        return version(saved, names(List.of(saved)));
    }

    // ------------------------------------------------------------------ helpers

    private int audit(UUID actor, PayPolicy saved, String field, Object before, Object after) {
        if (Objects.equals(before, after)) {
            return 0;
        }
        changes.record(actor, ENTITY, saved.getId(), field, before, after);
        return 1;
    }

    private PayPolicy versionOn(LocalDate date) {
        return repository
                .findFirstByEffectiveOnLessThanEqualOrderByEffectiveOnDescRecordedAtDesc(date)
                .orElseGet(() -> oldest(repository.findAllByOrderByEffectiveOnDescRecordedAtDesc()));
    }

    private static PayPolicy oldest(List<PayPolicy> newestFirst) {
        if (newestFirst.isEmpty()) {
            throw new IllegalStateException("The pay policy has no version; the migration seeds version 1.");
        }
        return newestFirst.get(newestFirst.size() - 1);
    }

    static BigDecimal fraction(BigDecimal raw) {
        if (raw == null) {
            throw new InvalidInputException("The half-day fraction is required.");
        }
        if (raw.signum() <= 0 || raw.compareTo(BigDecimal.ONE) >= 0) {
            throw new InvalidInputException("The half-day fraction must be more than 0 and less than 1.");
        }
        if (PayrollInput.decimals(raw) > 3) {
            throw new InvalidInputException("The half-day fraction can have at most three decimal places.");
        }
        return raw.setScale(3);
    }

    private static String fixed(BigDecimal value) {
        return value.setScale(3).toPlainString();
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String raw, String label) {
        if (raw == null || raw.isBlank()) {
            throw new InvalidInputException("The " + label + " is required.");
        }
        try {
            return Enum.valueOf(type, raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new InvalidInputException("Unknown " + label + " \"" + raw.trim() + "\".");
        }
    }

    private Map<UUID, String> names(List<PayPolicy> policies) {
        Set<UUID> ids = policies.stream()
                .map(PayPolicy::getRecordedBy)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        return ids.isEmpty()
                ? Map.of()
                : users.findAllById(ids).stream().collect(Collectors.toMap(AppUser::getId, AppUser::getDisplayName));
    }

    private static PolicyVersion version(PayPolicy p, Map<UUID, String> names) {
        UUID by = p.getRecordedBy();
        return new PolicyVersion(
                p.getId(),
                p.effectiveOn(),
                p.getLopDivisor(),
                fixed(p.getHalfDayFraction()),
                p.getRounding(),
                p.getPayMonth(),
                p.getNote(),
                by == null ? null : new Actor(by, names.getOrDefault(by, "Unknown user")),
                p.recordedAt());
    }
}
