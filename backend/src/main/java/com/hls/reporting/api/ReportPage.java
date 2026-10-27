package com.hls.reporting.api;

import java.util.List;

/**
 * The answer for one report: titled sections (for example Teachers, Schools, Zones, Managers), the state of the month and
 * notes for the reader. A section that could not be read is marked {@code failed} and has no rows; the others still load.
 */
public record ReportPage(
        String key, String title, String month, MonthState monthState, List<Section> sections, List<String> notes) {

    public enum Unit {
        COUNT,
        DAYS,
        PERCENT,
        RUPEES,
        TEXT
    }

    public record Column(String key, String label, Unit unit) {}

    /** One row; {@code cells} line up with the section's columns. {@code id} is the Teacher, School, Zone or person it is about. */
    public record Row(String id, String label, List<Cell> cells) {

        public Row {
            cells = List.copyOf(cells);
        }
    }

    public record Section(
            String key, String title, List<Column> columns, List<Row> rows, Row totals, boolean failed, String note) {

        public Section {
            columns = List.copyOf(columns);
            rows = List.copyOf(rows);
        }

        public static Section of(String key, String title, List<Column> columns, List<Row> rows, Row totals) {
            return new Section(key, title, columns, rows, totals, false, null);
        }

        public static Section failed(String key, String title, List<Column> columns, String note) {
            return new Section(key, title, columns, List.of(), null, true, note);
        }
    }

    public ReportPage {
        sections = List.copyOf(sections);
        notes = List.copyOf(notes);
    }
}
