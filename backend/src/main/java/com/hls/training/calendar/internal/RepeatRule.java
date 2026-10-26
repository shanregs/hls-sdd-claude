package com.hls.training.calendar.internal;

import java.time.LocalDate;
import java.util.List;

/**
 * The repeat marker of spec 024 (research.md section 10): a person with at least {@code threshold} Open or Acknowledged
 * exceptions whose session date falls within the last {@code windowMonths} months is marked as a repeat. Excused, Made up
 * and Withdrawn exceptions do not count. Plain rules, no Spring.
 */
public final class RepeatRule {

    private RepeatRule() {}

    /** One exception as the rule sees it. */
    public record Fact(LocalDate sessionDate, String status, String closureKind) {}

    public static boolean counts(Fact fact) {
        if (TrainingException.OPEN.equals(fact.status())) {
            return true;
        }
        return TrainingException.CLOSED.equals(fact.status()) && "ACKNOWLEDGED".equals(fact.closureKind());
    }

    /** The number of counting exceptions dated within the window ending on {@code today} (both ends included). */
    public static int count(List<Fact> facts, LocalDate today, int windowMonths) {
        LocalDate from = today.minusMonths(windowMonths);
        return (int) facts.stream()
                .filter(RepeatRule::counts)
                .filter(f -> !f.sessionDate().isBefore(from) && !f.sessionDate().isAfter(today))
                .count();
    }

    public static boolean isRepeat(List<Fact> facts, LocalDate today, int windowMonths, int threshold) {
        return count(facts, today, windowMonths) >= threshold;
    }
}
