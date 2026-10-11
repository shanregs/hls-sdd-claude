package com.hls.payroll.internal;

import com.hls.payroll.api.UnpaidDay;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Which payable dates are unpaid (spec 013a FR-012, research.md section 4). Pure: no clock, no repository.
 *
 * <p>Until spec 008's amendment A2 adds a loss-of-pay flag per day, an unpaid day is read from the status code
 * <strong>category</strong> of the attendance mark (never its letter, so a code added later with the right category is
 * picked up): a LEAVE-category mark that no approved leave request backs (an absence), or one made by a request of the
 * Loss-of-Pay type. A date with no mark is not classified here: it is an unmarked day, which attendance reports
 * separately and which payroll must not silently treat as paid or unpaid on its own.
 */
public final class UnpaidDayClassifier {

    /** The category value spec 008 gives a mark for leave and absence ({@code StatusCategory.LEAVE}). */
    public static final String LEAVE_CATEGORY = "LEAVE";

    /** One attendance mark: its category, its day value (1.00 or 0.50) and the leave request that made it, if any. */
    public record Mark(LocalDate date, String category, BigDecimal dayValue, UUID leaveRequestId) {}

    private UnpaidDayClassifier() {}

    public static List<UnpaidDay> classify(
            Collection<Mark> marks,
            Set<LocalDate> payableDates,
            Set<UUID> lossOfPayRequests,
            BigDecimal halfDayFraction) {
        return marks.stream()
                .filter(m -> payableDates.contains(m.date()))
                .filter(m -> LEAVE_CATEGORY.equals(m.category()))
                .filter(m -> m.leaveRequestId() == null || lossOfPayRequests.contains(m.leaveRequestId()))
                .map(m -> new UnpaidDay(m.date(), isFullDay(m) ? BigDecimal.ONE : halfDayFraction))
                .sorted(Comparator.comparing(UnpaidDay::date))
                .toList();
    }

    private static boolean isFullDay(Mark mark) {
        return mark.dayValue() == null || mark.dayValue().compareTo(BigDecimal.ONE) >= 0;
    }
}
