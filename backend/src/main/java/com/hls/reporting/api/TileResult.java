package com.hls.reporting.api;

import java.time.Instant;
import java.util.List;

/**
 * The answer for one tile. A number exists only in an {@code OK} result: the other states carry no values, so a missing
 * source can never be shown as 0.
 */
public record TileResult(
        String key,
        State state,
        List<Value> values,
        boolean organizationWide,
        String note,
        Instant takenAt,
        Table table) {

    public enum State {
        OK,
        NO_DATA,
        NOT_AVAILABLE,
        NOT_WORKING_DAY,
        ERROR
    }

    public enum Unit {
        COUNT,
        RUPEES,
        PERCENT,
        TEXT
    }

    /** One labelled figure; {@code value} is a number, or text when the unit is {@code TEXT}. */
    public record Value(String label, Object value, Unit unit, Link link) {

        public static Value count(String label, long value, Link link) {
            return new Value(label, value, Unit.COUNT, link);
        }

        public static Value rupees(String label, java.math.BigDecimal value, Link link) {
            return new Value(label, value, Unit.RUPEES, link);
        }

        public static Value percent(String label, double value, Link link) {
            return new Value(label, value, Unit.PERCENT, link);
        }

        public static Value text(String label, String value, Link link) {
            return new Value(label, value, Unit.TEXT, link);
        }
    }

    /** A table tile: column labels and rows of values. */
    public record Table(List<String> columns, List<List<Value>> rows) {

        public Table {
            columns = List.copyOf(columns);
            rows = List.copyOf(rows);
        }
    }

    public TileResult {
        values = values == null ? List.of() : List.copyOf(values);
        if (state != State.OK && (!values.isEmpty() || table != null)) {
            throw new IllegalArgumentException("Only an OK result may carry values");
        }
    }

    /** The only way to carry a number. */
    public static TileResult ok(String key, Value... values) {
        return new TileResult(key, State.OK, List.of(values), false, null, null, null);
    }

    public static TileResult ok(String key, Table table) {
        return new TileResult(key, State.OK, List.of(), false, null, null, table);
    }

    public static TileResult noData(String key, String note) {
        return new TileResult(key, State.NO_DATA, List.of(), false, note, null, null);
    }

    public static TileResult notAvailable(String key, String missing) {
        return new TileResult(key, State.NOT_AVAILABLE, List.of(), false, missing, null, null);
    }

    public static TileResult notWorkingDay(String key, String reason) {
        return new TileResult(key, State.NOT_WORKING_DAY, List.of(), false, reason, null, null);
    }

    /** Used by the area reader when a source throws or times out; sources do not call this. */
    public static TileResult error(String key, String note) {
        return new TileResult(key, State.ERROR, List.of(), false, note, null, null);
    }

    public TileResult stamped(Instant takenAt, boolean organizationWide) {
        return new TileResult(key, state, values, organizationWide, note, takenAt, table);
    }
}
