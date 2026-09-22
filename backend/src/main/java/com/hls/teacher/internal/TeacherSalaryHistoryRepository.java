package com.hls.teacher.internal;

import java.util.List;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/**
 * Deliberately narrower than {@code JpaRepository}: FR-004 requires salary
 * history entries be never edited or deleted, so no {@code delete}/
 * {@code deleteById} method is exposed here at all. Mirrors
 * {@code TeacherProfileRepository}'s established pattern.
 */
public interface TeacherSalaryHistoryRepository extends Repository<TeacherSalaryHistory, UUID> {

    TeacherSalaryHistory save(TeacherSalaryHistory entry);

    /** research.md §5: latest effective_from first, tie-broken by latest created_at. */
    List<TeacherSalaryHistory> findByTeacherIdOrderByEffectiveFromDescCreatedAtDesc(UUID teacherId);
}
