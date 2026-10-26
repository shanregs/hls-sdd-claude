package com.hls.training.calendar.internal;

import com.hls.attendance.api.AttendanceReadApi;
import com.hls.attendance.api.MarkView;
import com.hls.attendance.api.TrainingAttendance;
import com.hls.school.api.ConflictException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * The one place the Training Calendar writes to Teacher attendance (spec 024 FR-017, spec 008). A Teacher who attended has a
 * training-day mark; a person who was recorded present and then corrected loses it. Every write is guarded by pre-checks
 * through {@link AttendanceReadApi} so that a refusal never reaches {@link TrainingAttendance} (a failed write inside the
 * caller's transaction would roll the whole save back). Nothing here overwrites a School mark: a day that already holds a
 * different mark is a clash, reported to the Admin, and 008 is left unchanged.
 */
@Component("trainingAttendanceBridge")
public class AttendanceBridge {

    public static final String SAVED = "SAVED";
    public static final String SAVED_CLASH = "SAVED_CLASH";
    public static final String REFUSED = "REFUSED";
    public static final String MONTH_LOCKED = "MONTH_LOCKED";
    static final String TRAINING_CODE = "T";
    static final String LOCKED_MESSAGE = "This month is locked. Attendance can only change after it is reopened.";

    /** What happened to the 008 side of one row. {@code code} is set for a refusal. */
    public record Outcome(String status, String code, String message) {

        public boolean saved() {
            return SAVED.equals(status);
        }

        public boolean clash() {
            return SAVED_CLASH.equals(status);
        }

        public boolean refused() {
            return REFUSED.equals(status);
        }
    }

    private final AttendanceReadApi read;
    private final TrainingAttendance write;

    public AttendanceBridge(AttendanceReadApi read, TrainingAttendance write) {
        this.read = read;
        this.write = write;
    }

    /** Writes or updates the Teacher's training-day mark for the date; {@code value} is 1.00 or 0.50. */
    public Outcome markPresent(UUID actor, UUID teacherId, LocalDate date, BigDecimal value) {
        YearMonth month = YearMonth.from(date);
        if (read.isLocked(teacherId, month)) {
            return new Outcome(REFUSED, MONTH_LOCKED, LOCKED_MESSAGE);
        }
        MarkView existing = markOn(teacherId, date);
        if (existing != null && !TRAINING_CODE.equals(existing.code())) {
            return new Outcome(
                    SAVED_CLASH,
                    "CLASH",
                    "This Teacher already has a " + existing.codeName() + " mark on this day, so no training day was written. Nothing was changed.");
        }
        try {
            write.markTrainingDay(actor, teacherId, date, value);
        } catch (ConflictException e) {
            return new Outcome(REFUSED, "REFUSED", e.getMessage());
        }
        return new Outcome(SAVED, null, null);
    }

    /** Removes the Teacher's training-day mark after a correction away from Present; a School mark is never touched. */
    public Outcome clear(UUID actor, UUID teacherId, LocalDate date) {
        YearMonth month = YearMonth.from(date);
        MarkView existing = markOn(teacherId, date);
        if (existing == null || !TRAINING_CODE.equals(existing.code())) {
            return new Outcome(SAVED, null, null);
        }
        if (read.isLocked(teacherId, month)) {
            return new Outcome(REFUSED, MONTH_LOCKED, LOCKED_MESSAGE);
        }
        try {
            write.clearTrainingDay(actor, teacherId, date);
        } catch (ConflictException e) {
            return new Outcome(REFUSED, "REFUSED", e.getMessage());
        }
        return new Outcome(SAVED, null, null);
    }

    private MarkView markOn(UUID teacherId, LocalDate date) {
        return read.marksOf(teacherId, YearMonth.from(date)).stream()
                .filter(m -> date.equals(m.date()))
                .findFirst()
                .orElse(null);
    }
}
