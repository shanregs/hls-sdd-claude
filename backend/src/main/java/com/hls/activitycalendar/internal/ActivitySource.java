package com.hls.activitycalendar.internal;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * One source of dated activities for the calendar (drives, visits, tasks, and later training). A source is a bean; the
 * calendar calls each in its own try/catch so one failure gives a named note instead of an empty page.
 */
public interface ActivitySource {

    String DRIVES = "DRIVES";
    String VISITS = "VISITS";
    String TASKS = "TASKS";
    String TRAINING = "TRAINING";

    /** DRIVES, VISITS, TASKS or TRAINING. */
    String group();

    /** False when the module behind the source does not exist yet (training until spec 024). */
    boolean available();

    /** Every dated activity in the range, any status; a null user means everyone's. */
    List<CalendarEntry> entries(LocalDate from, LocalDate to, UUID userIdOrNull);
}
