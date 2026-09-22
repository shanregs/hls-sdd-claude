package com.hls.teacher.api;

import com.hls.teacher.api.dto.TeacherProfileView;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Public read surface for Teacher Profiles (FR-011). {@code exists} is the
 * forward hook a future touch to Organization's FR-012 "unknown identifier"
 * branch could call — not wired up by this module (research.md §9).
 *
 * <p>{@code findAll} is a small, additive extension for specs/011-attendance's
 * grid (User Story 5) — a Director's unfiltered "every Teacher" row set
 * (specs/011-attendance research.md §8). Nothing else in this interface changed.
 */
public interface TeacherQueries {

    Optional<TeacherProfileView> findById(UUID teacherId);

    boolean exists(UUID teacherId);

    List<TeacherProfileView> findAll();
}
