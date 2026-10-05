package com.hls.teacher.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * How recruitment (spec 016) creates and moves Teachers without reaching into the teacher module: a new Teacher
 * starts IN_TRAINING with no salary, becomes ACTIVE after induction, and gets the salary of the accepted offer
 * once, on the first assignment to a School. Every change is audited as if made by the acting user.
 */
public interface TeacherRegistry {

    record Candidate(String name, String phone, String email, String address) {}

    /** An existing Teacher sharing the phone or email; {@code status} is the Teacher status name. */
    record Match(UUID teacherId, String name, String status) {}

    /** Teachers with the same normalized phone (last ten digits) or the same email ignoring case. */
    List<Match> findMatches(String phone, String email);

    /** Creates a Teacher with status IN_TRAINING and no salary entry. */
    UUID createTrainee(UUID actor, Candidate details);

    /** IN_TRAINING to ACTIVE; any other status is a conflict. */
    void activate(UUID actor, UUID teacherId);

    /** Moves the Teacher to EXITED following the status machine, recording the reason. */
    void exit(UUID actor, UUID teacherId, LocalDate on, String reason);

    boolean hasSalaryEntry(UUID teacherId);

    /** Writes the first salary entry; a conflict if the Teacher already has one. */
    void recordFirstSalary(UUID actor, UUID teacherId, BigDecimal amount, LocalDate effectiveOn);

    Set<UUID> activeTeacherIds();
}
