package com.hls.attendance.workingdays.internal;

import com.hls.school.api.InvalidInputException;
import java.math.BigDecimal;

/**
 * What to do with one month of a year save (spec 038, data-model.md "Month status rules"). Pure: no repository and no
 * clock. The figure is checked first, then the permission the month needs, then the stored state. A stored figure that
 * already equals the typed one is {@link Kind#UNCHANGED}, never a conflict (clarification 2026-10-16).
 */
public record MonthDecision(Kind kind, String message, BigDecimal figure) {

    public enum Kind {
        CREATE,
        UPDATE,
        UNCHANGED,
        INVALID_FIGURE,
        NOT_PERMITTED,
        STALE,
        ALREADY_EXISTS,
        ROW_DELETED
    }

    public static final String STALE_MESSAGE = "This figure was changed by someone else. The current figure is shown.";
    public static final String EXISTS_MESSAGE =
            "A different figure was saved for this month by someone else. The current figure is shown.";
    public static final String DELETED_MESSAGE = "This figure was deleted by someone else.";

    /**
     * @param typed the figure the user typed
     * @param version the version the screen loaded, {@code null} when it loaded no figure for the month
     * @param current the row stored now, {@code null} when there is none
     */
    public static MonthDecision decide(
            BigDecimal typed,
            Long version,
            SchoolWorkingDaysRow current,
            boolean canCreate,
            boolean canEdit,
            int year,
            int month) {
        BigDecimal figure;
        try {
            figure = WorkingDaysRules.checkFigure(typed, year, month);
        } catch (InvalidInputException e) {
            return new MonthDecision(Kind.INVALID_FIGURE, e.getMessage(), null);
        }
        boolean creating = version == null;
        if (creating ? !canCreate : !canEdit) {
            return new MonthDecision(
                    Kind.NOT_PERMITTED,
                    "You are not allowed to " + (creating ? "add" : "change") + " this figure.",
                    figure);
        }
        if (creating) {
            if (current == null) {
                return new MonthDecision(Kind.CREATE, null, figure);
            }
            return same(figure, current)
                    ? new MonthDecision(Kind.UNCHANGED, null, figure)
                    : new MonthDecision(Kind.ALREADY_EXISTS, EXISTS_MESSAGE, figure);
        }
        if (current == null) {
            return new MonthDecision(Kind.ROW_DELETED, DELETED_MESSAGE, figure);
        }
        if (same(figure, current)) {
            return new MonthDecision(Kind.UNCHANGED, null, figure);
        }
        return version.equals(current.getVersion())
                ? new MonthDecision(Kind.UPDATE, null, figure)
                : new MonthDecision(Kind.STALE, STALE_MESSAGE, figure);
    }

    private static boolean same(BigDecimal figure, SchoolWorkingDaysRow current) {
        return figure.compareTo(current.getWorkingDays()) == 0;
    }
}
