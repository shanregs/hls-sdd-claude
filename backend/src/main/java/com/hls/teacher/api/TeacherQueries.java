package com.hls.teacher.api;

import com.hls.teacher.api.dto.TeacherProfileView;
import java.util.Optional;
import java.util.UUID;

/**
 * Public read surface for Teacher Profiles (FR-011). {@code exists} is the
 * forward hook a future touch to Organization's FR-012 "unknown identifier"
 * branch could call — not wired up by this module (research.md §9).
 */
public interface TeacherQueries {

    Optional<TeacherProfileView> findById(UUID teacherId);

    boolean exists(UUID teacherId);
}
