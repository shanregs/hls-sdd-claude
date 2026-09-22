package com.hls.attendance.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Three states (data-model.md): explicit mark (all of {@code statusCode}/
 * {@code category}/{@code fractionalValue}/{@code schoolId} set); calendar
 * non-working with no mark ({@code category=NON_WORKING}, the rest null);
 * truly unmarked (all null, FR-019's "clear unmarked indicator"). {@code
 * editable} is a UI hint only (FR-025) — the edit call independently
 * re-checks lock and permission. {@code schoolId} lets a grid-originated edit
 * of an already-marked day pass the same school assignment straight back
 * through {@code MarkAttendanceRequest} without the caller re-typing it
 * (an implementation-time addition — the day the grid's inline edit was
 * built, mirroring how {@code AttendanceTeacherNotFoundException} was added).
 */
public record GridCell(String statusCode, AttendanceCategory category, BigDecimal fractionalValue, UUID schoolId, boolean editable) {
}
