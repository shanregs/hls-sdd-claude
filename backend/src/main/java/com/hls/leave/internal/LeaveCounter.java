package com.hls.leave.internal;

import com.hls.attendance.api.LeaveAttendance.LeaveDay;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Pure counting rules for a leave request (spec 009 research.md section 4): every working day counts 1,
 * a half-day start or end makes the first or last <em>working</em> day count 0.5. No Spring, no database.
 */
public final class LeaveCounter {

    private static final BigDecimal WHOLE = new BigDecimal("1.00");
    private static final BigDecimal HALF = new BigDecimal("0.50");

    private LeaveCounter() {}

    /** The days a request covers with their day values, or a refusal reason in {@code problem}. */
    public record Counted(List<LeaveDay> days, BigDecimal total, String problem) {

        public boolean ok() {
            return problem == null;
        }
    }

    public static Counted count(List<LeaveDay> workingDays, boolean halfStart, boolean halfEnd) {
        if (workingDays.isEmpty()) {
            return new Counted(List.of(), BigDecimal.ZERO, "None of these dates is a working day for you.");
        }
        List<LeaveDay> sorted = workingDays.stream().sorted(Comparator.comparing(LeaveDay::date)).toList();
        if (sorted.size() == 1 && halfStart && halfEnd) {
            return new Counted(List.of(), BigDecimal.ZERO, "A single working day can be a half day at the start or at the end, not both.");
        }
        List<LeaveDay> result = new ArrayList<>(sorted.size());
        BigDecimal total = BigDecimal.ZERO;
        for (int i = 0; i < sorted.size(); i++) {
            LeaveDay day = sorted.get(i);
            boolean half = (i == 0 && halfStart) || (i == sorted.size() - 1 && halfEnd);
            BigDecimal value = half ? HALF : WHOLE;
            result.add(new LeaveDay(day.date(), day.schoolId(), value));
            total = total.add(value);
        }
        return new Counted(List.copyOf(result), total, null);
    }
}
