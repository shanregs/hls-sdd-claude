package com.hls.attendance.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Induction attendance for the training module (spec 016, amendment A5 to spec 008): a training day is marked
 * before the Teacher has a School, so it carries none. Marks keep their history, respect the month lock and are the
 * single source of truth for induction attendance.
 */
public interface TrainingAttendance {

    /** Writes or corrects the Teacher's training-day mark; {@code value} is 1.00 or 0.50. */
    void markTrainingDay(UUID actor, UUID teacherId, LocalDate date, BigDecimal value);

    /** Removes a training-day mark; a conflict if the day holds a different kind of mark, none if no mark. */
    void clearTrainingDay(UUID actor, UUID teacherId, LocalDate date);
}
