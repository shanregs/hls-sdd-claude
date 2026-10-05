package com.hls.schoolbilling.api;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Published when a Teacher is assigned to a School for the first time (amendment A4 to spec 012), in the same
 * transaction as the assignment. Spec 016 listens to write the salary of the accepted offer; it is not published for
 * a later move, a re-map or a scheduled change.
 */
public record TeacherFirstAssigned(UUID teacherId, UUID schoolId, LocalDate startsOn) {}
