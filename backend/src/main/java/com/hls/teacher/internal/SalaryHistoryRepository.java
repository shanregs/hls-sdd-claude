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

    /** Every entry of the given Teachers on or before {@code date}, for resolving the one in effect in memory. */
    @org.springframework.data.jpa.repository.Query(
            "select e from SalaryHistoryEntry e where e.teacherId in :teacherIds and e.effectiveOn <= :date")
    List<SalaryHistoryEntry> findInEffectCandidates(
            @org.springframework.data.repository.query.Param("teacherIds") java.util.Collection<UUID> teacherIds,
            @org.springframework.data.repository.query.Param("date") LocalDate date);

    /** The latest entry in effect on {@code date}; ties on the same day resolve to the newest recording. */
    Optional<SalaryHistoryEntry> findFirstByTeacherIdAndEffectiveOnLessThanEqualOrderByEffectiveOnDescCreatedAtDesc(
            UUID teacherId, LocalDate date);
}
