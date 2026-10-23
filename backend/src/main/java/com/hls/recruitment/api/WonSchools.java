package com.hls.recruitment.api;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Schools won in a period (spec 015 cost per School acquired). Reads the date a prospect was won, as a business date in
 * Asia/Kolkata, optionally narrowed to one Zone. Read-only; callers apply their own permission and scope.
 */
public interface WonSchools {

    /** One won prospect; {@code schoolId} is null until the School record is created. */
    record WonSchool(UUID prospectId, UUID schoolId, LocalDate wonOn, UUID zoneId) {}

    /** Prospects won on a date between {@code from} and {@code to}, both inclusive; a null zone means every Zone. */
    List<WonSchool> wonBetween(LocalDate from, LocalDate to, UUID zoneIdOrNull);
}
