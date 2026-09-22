package com.hls.attendance.api.dto;

import java.time.LocalDate;
import java.util.UUID;

/** FR-022. The read-side shape of one Non-Working Calendar date. */
public record NonWorkingDateView(UUID id, LocalDate date, String label, boolean active) {
}
