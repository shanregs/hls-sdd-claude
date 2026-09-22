package com.hls.teacher.api.dto;

import java.math.BigDecimal;

/**
 * FR-005/006/007. One of two states for a teacher's salary at a given
 * point in time — same "answer" pattern
 * {@code school.api.dto.SchoolZoneAnswer} already established for "current
 * value or a clear not-yet-assigned state."
 */
public record SalaryAsOfAnswer(State state, BigDecimal amount) {

    public enum State {
        RECORDED,
        NOT_YET_RECORDED
    }

    public static SalaryAsOfAnswer recorded(BigDecimal amount) {
        return new SalaryAsOfAnswer(State.RECORDED, amount);
    }

    public static SalaryAsOfAnswer notYetRecorded() {
        return new SalaryAsOfAnswer(State.NOT_YET_RECORDED, null);
    }
}
