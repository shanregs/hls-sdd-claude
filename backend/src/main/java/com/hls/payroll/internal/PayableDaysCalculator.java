package com.hls.payroll.internal;

import com.hls.payroll.api.Span;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;

/**
 * The working dates of a month a person is payable for (spec 013a FR-010, FR-011). Pure: no clock, no repository.
 * A date counts only when it lies inside an employment span and is a working day on that span's own calendar, and a
 * date belongs to at most one span, so a transfer day is never counted twice and never lost.
 */
public final class PayableDaysCalculator {

    /** A stretch of employment; {@code from} or {@code to} null means open at that end. {@code schoolId} null: default calendar. */
    public record Employment(UUID schoolId, LocalDate from, LocalDate to) {}

    /** {@code schoolByDate} says which span each payable date was counted in. */
    public record Result(List<LocalDate> dates, List<Span> spans, Map<LocalDate, UUID> schoolByDate, UUID lastSchoolId) {}

    private PayableDaysCalculator() {}

    /**
     * @param workingDaysBySchool the working dates of the whole month for each School in {@code employment}, and
     *     under the null key for the default calendar
     */
    public static Result compute(YearMonth month, List<Employment> employment, Map<UUID, List<LocalDate>> workingDaysBySchool) {
        LocalDate first = month.atDay(1);
        LocalDate last = month.atEndOfMonth();
        List<Employment> ordered = new ArrayList<>(employment);
        ordered.sort(Comparator.comparing(e -> e.from() == null ? LocalDate.MIN : e.from()));

        TreeSet<LocalDate> taken = new TreeSet<>();
        Map<LocalDate, UUID> schoolByDate = new HashMap<>();
        List<Span> spans = new ArrayList<>();
        UUID lastSchool = null;
        for (Employment e : ordered) {
            LocalDate from = e.from() == null || e.from().isBefore(first) ? first : e.from();
            LocalDate to = e.to() == null || e.to().isAfter(last) ? last : e.to();
            if (from.isAfter(to)) {
                continue;
            }
            int counted = 0;
            for (LocalDate date : workingDaysBySchool.getOrDefault(e.schoolId(), List.of())) {
                if (!date.isBefore(from) && !date.isAfter(to) && taken.add(date)) {
                    schoolByDate.put(date, e.schoolId());
                    counted++;
                }
            }
            spans.add(new Span(e.schoolId(), from, to, counted));
            lastSchool = e.schoolId();
        }
        return new Result(List.copyOf(taken), List.copyOf(spans), schoolByDate, lastSchool);
    }
}
