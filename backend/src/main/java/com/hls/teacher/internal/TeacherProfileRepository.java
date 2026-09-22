package com.hls.teacher.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/**
 * Deliberately narrower than {@code JpaRepository}: FR-004 requires Teacher
 * Profiles be never deleted, so no {@code delete}/{@code deleteById} method
 * is exposed here at all. Mirrors {@code ZoneRepository}/{@code PlaceRepository}'s
 * established pattern.
 */
public interface TeacherProfileRepository extends Repository<TeacherProfile, UUID> {

    TeacherProfile save(TeacherProfile profile);

    Optional<TeacherProfile> findById(UUID id);

    List<TeacherProfile> findAll();
}
