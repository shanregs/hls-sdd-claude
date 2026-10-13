package com.hls.attendance.manager.internal;

import com.hls.attendance.api.UnpaidDay;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * The text kept in {@code manager_month.frozen_unpaid_dates}: each unpaid date with its fraction as
 * {@code yyyy-MM-dd:1.0}, separated by commas, oldest first. Written when a month is locked and read back for payroll.
 */
final class FrozenUnpaidDates {

    private FrozenUnpaidDates() {}

    static String encode(List<UnpaidDay> days) {
        StringBuilder out = new StringBuilder();
        for (UnpaidDay day : days) {
            if (out.length() > 0) {
                out.append(',');
            }
            out.append(day.date()).append(':').append(day.fraction().toPlainString());
        }
        return out.toString();
    }

    static List<UnpaidDay> decode(String text) {
        List<UnpaidDay> days = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return days;
        }
        for (String part : text.split(",")) {
            String[] pair = part.split(":");
            days.add(new UnpaidDay(LocalDate.parse(pair[0]), new BigDecimal(pair[1])));
        }
        return days;
    }
}
