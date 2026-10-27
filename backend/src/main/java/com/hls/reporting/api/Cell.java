package com.hls.reporting.api;

/**
 * One figure of a report row. A number exists only in a {@code VALUE} cell: a missing source is never shown as 0
 * (spec 014a FR-004).
 */
public record Cell(State state, Object value) {

    public enum State {
        VALUE,
        /** Nothing recorded yet (for example no working days or no run). */
        NO_DATA,
        /** The source could not be read. */
        NOT_AVAILABLE,
        /** The thing asked for does not match the row (for example a Teacher outside the filter). */
        NOT_MATCHED
    }

    public Cell {
        if (state != State.VALUE && value != null) {
            throw new IllegalArgumentException("Only a VALUE cell may carry a value");
        }
        if (state == State.VALUE && value == null) {
            throw new IllegalArgumentException("A value cell needs a value; use noData() when there is none");
        }
    }

    public static Cell of(Object value) {
        return new Cell(State.VALUE, value);
    }

    public static Cell noData() {
        return new Cell(State.NO_DATA, null);
    }

    public static Cell notAvailable() {
        return new Cell(State.NOT_AVAILABLE, null);
    }

    public static Cell notMatched() {
        return new Cell(State.NOT_MATCHED, null);
    }
}
