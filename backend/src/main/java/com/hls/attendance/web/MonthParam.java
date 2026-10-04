package com.hls.attendance.web;

import com.hls.school.api.InvalidInputException;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;

/** Parses the {@code YYYY-MM} month parameter used by every attendance endpoint. */
final class MonthParam {

    private MonthParam() {}

    static YearMonth parse(String value) {
        if (value == null || value.isBlank()) {
            throw new InvalidInputException("The month is required (YYYY-MM).");
        }
        try {
            return YearMonth.parse(value.trim());
        } catch (DateTimeParseException e) {
            throw new InvalidInputException("The month must look like 2026-10.");
        }
    }
}
