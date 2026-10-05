package com.hls.attendance.internal;

import com.hls.attendance.api.TrainingAttendance;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
class TrainingAttendanceImpl implements TrainingAttendance {

    private final MarkService markService;

    TrainingAttendanceImpl(MarkService markService) {
        this.markService = markService;
    }

    @Override
    public void markTrainingDay(UUID actor, UUID teacherId, LocalDate date, BigDecimal value) {
        markService.setTrainingMark(actor, teacherId, date, value);
    }

    @Override
    public void clearTrainingDay(UUID actor, UUID teacherId, LocalDate date) {
        markService.clearTrainingMark(actor, teacherId, date);
    }
}
