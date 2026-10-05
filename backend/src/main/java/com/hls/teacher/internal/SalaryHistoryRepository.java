package com.hls.teacher.internal;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Save and query only: there is no update or delete (spec 005 FR-017). */
public interface SalaryHistoryRepository extends JpaRepository<SalaryHistoryEntry, UUID> {

    boolean existsByTeacherId(UUID teacherId);

    List<SalaryHistoryEntry> findByTeacherIdOrderByEffectiveOnDescCreatedAtDesc(UUID teacherId);

    /** The latest entry in effect on {@code date}; ties on the same day resolve to the newest recording. */
    Optional<SalaryHistoryEntry> findFirstByTeacherIdAndEffectiveOnLessThanEqualOrderByEffectiveOnDescCreatedAtDesc(
            UUID teacherId, LocalDate date);
}
