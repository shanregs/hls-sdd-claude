package com.hls.substitution.payout;

import com.hls.identity.user.Role;
import com.hls.school.api.NotFoundException;
import com.hls.school.api.SchoolDirectory;
import com.hls.school.api.SchoolDirectory.SchoolInfo;
import com.hls.school.api.SchoolDirectory.ZoneInfo;
import com.hls.substitution.internal.SubstitutionScope;
import com.hls.teacher.api.TeacherDirectory;
import com.hls.teacher.api.TeacherDirectory.TeacherInfo;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads of the substitute days in the caller's scope (spec 017b FR-018, FR-022): filters, paging, totals over the whole
 * filter, and names looked up in batches. A Zone Manager sees only the Schools they manage; anything else is 404.
 */
@Service
@Transactional(readOnly = true)
public class DayQueryService {

    /** One row of the list. {@code actions} are what the caller may do with it (Principle IV). */
    public record DayView(
            UUID id,
            UUID substituteTeacherId,
            String substituteName,
            UUID schoolId,
            String schoolName,
            UUID zoneId,
            String zoneName,
            LocalDate date,
            BigDecimal dayValue,
            boolean live,
            String attendance,
            String status,
            String statusText,
            String mismatchReason,
            BigDecimal rate,
            BigDecimal amount,
            LocalDate runMonth,
            LocalDate workMonth,
            BigDecimal paidAmount,
            boolean needsReview,
            boolean locked,
            Long version,
            List<String> actions) {}

    /** A day that needs attention, with why and what to do next (spec 017b FR-019). */
    public record MismatchView(DayView day, String reason, String nextStep, BigDecimal suggestedCorrection) {}

    /** Totals over the whole filter, not only the page (SC-002). */
    public record Totals(
            long allocated, long attended, long paid, Map<String, Long> unpaidByStatus, BigDecimal amountPaid) {}

    public record Page(List<DayView> content, int page, int size, long totalElements, Totals totals) {}

    /** The filters of the list; every member is optional. */
    public record Filter(
            LocalDate from,
            LocalDate to,
            UUID substituteTeacherId,
            UUID schoolId,
            UUID zoneId,
            Set<DayStatus> statuses) {}

    private final SubstituteDayRepository days;
    private final MonthLockRepository locks;
    private final SubstitutionScope scope;
    private final SchoolDirectory schools;
    private final TeacherDirectory teachers;

    DayQueryService(
            SubstituteDayRepository days,
            MonthLockRepository locks,
            SubstitutionScope scope,
            SchoolDirectory schools,
            TeacherDirectory teachers) {
        this.days = days;
        this.locks = locks;
        this.scope = scope;
        this.schools = schools;
        this.teachers = teachers;
    }

    public Page list(UUID actor, Set<Role> roles, Filter filter, boolean canEdit, int page, int size) {
        List<SubstituteDay> all = days.findAll(specification(actor, roles, filter), org.springframework.data.domain.Sort.by(
                org.springframework.data.domain.Sort.Order.desc("dayDate"), org.springframework.data.domain.Sort.Order.asc("id")));
        int from = Math.min(Math.max(page, 0) * Math.max(size, 1), all.size());
        int to = Math.min(from + Math.max(size, 1), all.size());
        List<DayView> content = views(all.subList(from, to), canEdit);
        return new Page(content, Math.max(page, 0), size, all.size(), totals(all));
    }

    /** All matching days, for the CSV export; no paging. */
    public List<DayView> all(UUID actor, Set<Role> roles, Filter filter) {
        List<SubstituteDay> all = days.findAll(specification(actor, roles, filter), org.springframework.data.domain.Sort.by(
                org.springframework.data.domain.Sort.Order.asc("dayDate"), org.springframework.data.domain.Sort.Order.asc("id")));
        return views(all, false);
    }

    private static final Set<DayStatus> MISMATCH_STATUSES = java.util.EnumSet.of(
            DayStatus.CANNOT_PAY,
            DayStatus.CHANGED_RUN_AWAITING,
            DayStatus.CORRECTION_NEEDED,
            DayStatus.CORRECTION_RAISED,
            DayStatus.BACKPAY_WAITING);

    /** Days that cannot be paid as they stand: the five problem statuses and a paid day now worth something else. */
    public List<MismatchView> mismatches(UUID actor, Set<Role> roles, Filter filter, boolean canEdit) {
        List<SubstituteDay> all = days.findAll(
                specification(actor, roles, new Filter(filter.from(), filter.to(), filter.substituteTeacherId(), filter.schoolId(), filter.zoneId(), Set.of())),
                org.springframework.data.domain.Sort.by(
                        org.springframework.data.domain.Sort.Order.asc("dayDate"), org.springframework.data.domain.Sort.Order.asc("id")));
        List<SubstituteDay> flagged = all.stream()
                .filter(d -> MISMATCH_STATUSES.contains(d.getStatus()) || differsFromPayment(d))
                .toList();
        List<DayView> views = views(flagged, canEdit);
        List<MismatchView> result = new ArrayList<>();
        for (int i = 0; i < flagged.size(); i++) {
            SubstituteDay d = flagged.get(i);
            result.add(new MismatchView(views.get(i), reasonOf(d), nextStepOf(d), suggested(d)));
        }
        return result;
    }

    private static boolean differsFromPayment(SubstituteDay d) {
        return d.getStatus() == DayStatus.PAID
                && d.getPaidAmount() != null
                && d.getAmount() != null
                && d.getPaidAmount().compareTo(d.getAmount()) != 0;
    }

    private static String reasonOf(SubstituteDay d) {
        if (d.getMismatchReason() != null) {
            return d.getMismatchReason();
        }
        if (differsFromPayment(d)) {
            return "Paid " + d.getPaidAmount().toPlainString() + ", now worth " + d.getAmount().toPlainString() + ".";
        }
        return d.getStatus().text();
    }

    private static String nextStepOf(SubstituteDay d) {
        if (differsFromPayment(d)) {
            return "The rate changed after payment. Raise a Correction if the difference should be paid.";
        }
        return switch (d.getStatus()) {
            case CANNOT_PAY -> "Add the missing designation or day rate; the next run picks the day up.";
            case CHANGED_RUN_AWAITING -> "Return the run, then compute it again.";
            case CORRECTION_NEEDED -> "Ask an Admin to raise a Correction.";
            case CORRECTION_RAISED -> "The Correction is waiting for the next pay run.";
            case BACKPAY_WAITING -> "Start a supplementary run for the month.";
            default -> "";
        };
    }

    /** What the Correction would be: the day's worth if it was added late, minus what was paid if it was withdrawn. */
    private static BigDecimal suggested(SubstituteDay d) {
        if (d.getStatus() != DayStatus.CORRECTION_NEEDED) {
            return null;
        }
        if (d.isLive() && d.getAttendance() == DayAttendance.PRESENT && d.getPaidAmount() == null) {
            return d.getAmount();
        }
        return d.getPaidAmount() == null ? null : d.getPaidAmount().negate();
    }

    public DayView get(UUID actor, Set<Role> roles, UUID dayId, boolean canEdit) {
        SubstituteDay day = days.findById(dayId).orElseThrow(() -> new NotFoundException("Substitute day not found."));
        scope.require(actor, roles, day.getSchoolId());
        return views(List.of(day), canEdit).get(0);
    }

    public List<DayEvent> history(UUID actor, Set<Role> roles, UUID dayId, DayEventRepository events) {
        SubstituteDay day = days.findById(dayId).orElseThrow(() -> new NotFoundException("Substitute day not found."));
        scope.require(actor, roles, day.getSchoolId());
        return events.findByDayIdOrderByAtAscIdAsc(dayId);
    }

    /** Converts days to rows with names, lock state and actions, in a fixed number of queries. */
    public List<DayView> views(Collection<SubstituteDay> source, boolean canEdit) {
        if (source.isEmpty()) {
            return List.of();
        }
        Set<UUID> teacherIds = source.stream().map(SubstituteDay::getSubstituteTeacherId).collect(Collectors.toSet());
        Set<UUID> schoolIds = source.stream().map(SubstituteDay::getSchoolId).collect(Collectors.toSet());
        Set<UUID> zoneIds = source.stream().map(SubstituteDay::getZoneId).collect(Collectors.toSet());
        Map<UUID, String> teacherNames = new HashMap<>();
        for (TeacherInfo t : teachers.teacherInfo(teacherIds).values()) {
            teacherNames.put(t.id(), t.name());
        }
        Map<UUID, String> schoolNames = schools.schools(schoolIds).stream()
                .collect(Collectors.toMap(SchoolInfo::id, SchoolInfo::name));
        Map<UUID, String> zoneNames = schools.zones(zoneIds).stream().collect(Collectors.toMap(ZoneInfo::id, ZoneInfo::name));
        LocalDate min = source.stream().map(SubstituteDay::getDayDate).min(Comparator.naturalOrder()).orElseThrow();
        LocalDate max = source.stream().map(SubstituteDay::getDayDate).max(Comparator.naturalOrder()).orElseThrow();
        Set<String> lockedKeys = new HashSet<>();
        for (MonthLock lock : locks.of(teacherIds, min.withDayOfMonth(1), max.withDayOfMonth(1))) {
            if (lock.isLocked()) {
                lockedKeys.add(lock.getSubstituteTeacherId() + "|" + lock.month());
            }
        }
        List<DayView> rows = new ArrayList<>();
        for (SubstituteDay d : source) {
            boolean locked = lockedKeys.contains(d.getSubstituteTeacherId() + "|" + d.month());
            List<String> actions = new ArrayList<>();
            if (canEdit && d.isLive() && !locked) {
                actions.add("MARK");
            }
            rows.add(new DayView(
                    d.getId(),
                    d.getSubstituteTeacherId(),
                    teacherNames.getOrDefault(d.getSubstituteTeacherId(), "?"),
                    d.getSchoolId(),
                    schoolNames.getOrDefault(d.getSchoolId(), "?"),
                    d.getZoneId(),
                    zoneNames.getOrDefault(d.getZoneId(), "?"),
                    d.getDayDate(),
                    d.getDayValue(),
                    d.isLive(),
                    d.getAttendance().name(),
                    d.getStatus().name(),
                    d.getStatus().text(),
                    d.getMismatchReason(),
                    d.getRate(),
                    d.getAmount(),
                    d.getRunMonth(),
                    d.getDayDate().withDayOfMonth(1),
                    d.getPaidAmount(),
                    d.isNeedsReview(),
                    locked,
                    d.getVersion(),
                    List.copyOf(actions)));
        }
        return rows;
    }

    Totals totals(List<SubstituteDay> all) {
        long allocated = all.stream().filter(SubstituteDay::isLive).count();
        long attended = all.stream()
                .filter(d -> d.isLive() && d.getAttendance() == DayAttendance.PRESENT)
                .count();
        long paid = all.stream().filter(d -> d.getStatus() == DayStatus.PAID).count();
        Map<DayStatus, Long> byStatus = new EnumMap<>(DayStatus.class);
        for (SubstituteDay d : all) {
            if (d.getStatus() != DayStatus.PAID
                    && d.getStatus() != DayStatus.CANCELLED
                    && d.getStatus() != DayStatus.DID_NOT_ATTEND
                    && d.getStatus() != DayStatus.CORRECTED) {
                byStatus.merge(d.getStatus(), 1L, Long::sum);
            }
        }
        Map<String, Long> unpaid = new java.util.LinkedHashMap<>();
        byStatus.forEach((k, v) -> unpaid.put(k.name(), v));
        BigDecimal amountPaid = all.stream()
                .map(SubstituteDay::getPaidAmount)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new Totals(allocated, attended, paid, unpaid, amountPaid);
    }

    private Specification<SubstituteDay> specification(UUID actor, Set<Role> roles, Filter f) {
        SubstitutionScope.Scope visible = scope.of(actor, roles);
        return (root, query, cb) -> {
            List<Predicate> all = new ArrayList<>();
            if (!visible.all()) {
                all.add(root.get("schoolId").in(visible.schoolIds()));
            }
            if (f.from() != null) {
                all.add(cb.greaterThanOrEqualTo(root.get("dayDate"), f.from()));
            }
            if (f.to() != null) {
                all.add(cb.lessThanOrEqualTo(root.get("dayDate"), f.to()));
            }
            if (f.substituteTeacherId() != null) {
                all.add(cb.equal(root.get("substituteTeacherId"), f.substituteTeacherId()));
            }
            if (f.schoolId() != null) {
                all.add(cb.equal(root.get("schoolId"), f.schoolId()));
            }
            if (f.zoneId() != null) {
                all.add(cb.equal(root.get("zoneId"), f.zoneId()));
            }
            if (f.statuses() != null && !f.statuses().isEmpty()) {
                all.add(root.get("status").in(f.statuses()));
            }
            return cb.and(all.toArray(new Predicate[0]));
        };
    }
}
