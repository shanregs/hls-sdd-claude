package com.hls.recruitment.internal;

import com.hls.school.api.InvalidInputException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Parses the candidate CSV: a header of {@code name,phone,email,degree,year,notes} (name and phone required). */
final class CsvImporter {

    static final int MAX_ROWS = 5000;
    private static final Set<String> KNOWN = Set.of("name", "phone", "email", "degree", "year", "notes");

    /** One data row; {@code number} is its line in the file counting the header as line 1. */
    record Row(int number, String name, String phone, String email, String degree, String year, String notes) {}

    private CsvImporter() {}

    static List<Row> parse(byte[] content) {
        if (content == null || content.length == 0) {
            throw new InvalidInputException("The file is empty.");
        }
        String text = new String(content, StandardCharsets.UTF_8);
        if (text.startsWith("﻿")) {
            text = text.substring(1);
        }
        List<List<String>> lines = split(text);
        if (lines.isEmpty()) {
            throw new InvalidInputException("The file is empty.");
        }
        Map<String, Integer> columns = new HashMap<>();
        List<String> header = lines.get(0);
        for (int i = 0; i < header.size(); i++) {
            String key = header.get(i).trim().toLowerCase();
            if (!KNOWN.contains(key) || columns.put(key, i) != null) {
                throw new InvalidInputException("The header must be name,phone,email,degree,year,notes (name and phone are required).");
            }
        }
        if (!columns.containsKey("name") || !columns.containsKey("phone")) {
            throw new InvalidInputException("The header must include name and phone.");
        }
        if (lines.size() - 1 > MAX_ROWS) {
            throw new InvalidInputException("A file can have at most " + MAX_ROWS + " rows.");
        }
        List<Row> rows = new ArrayList<>();
        for (int i = 1; i < lines.size(); i++) {
            List<String> cells = lines.get(i);
            if (cells.stream().allMatch(String::isBlank)) {
                continue;
            }
            rows.add(new Row(
                    i + 1,
                    cell(cells, columns, "name"),
                    cell(cells, columns, "phone"),
                    cell(cells, columns, "email"),
                    cell(cells, columns, "degree"),
                    cell(cells, columns, "year"),
                    cell(cells, columns, "notes")));
        }
        return rows;
    }

    private static String cell(List<String> cells, Map<String, Integer> columns, String key) {
        Integer index = columns.get(key);
        return index == null || index >= cells.size() ? null : cells.get(index);
    }

    /** Splits CSV text into rows of cells, honouring quoted cells that contain commas, quotes or line breaks. */
    private static List<List<String>> split(String text) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (quoted) {
                if (ch == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
                        cell.append('"');
                        i++;
                    } else {
                        quoted = false;
                    }
                } else {
                    cell.append(ch);
                }
            } else if (ch == '"') {
                quoted = true;
            } else if (ch == ',') {
                row.add(cell.toString());
                cell.setLength(0);
            } else if (ch == '\n' || ch == '\r') {
                if (ch == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') {
                    i++;
                }
                row.add(cell.toString());
                cell.setLength(0);
                rows.add(row);
                row = new ArrayList<>();
            } else {
                cell.append(ch);
            }
        }
        if (cell.length() > 0 || !row.isEmpty()) {
            row.add(cell.toString());
            rows.add(row);
        }
        return rows;
    }
}
