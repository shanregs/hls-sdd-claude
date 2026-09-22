package com.hls.school.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/**
 * Deliberately narrower than {@code JpaRepository}: FR-009 requires Zones be
 * never deleted, so no {@code delete}/{@code deleteById} method is exposed
 * here at all — there is nothing to accidentally call. Mirrors
 * {@code audit.internal.AuditEntryRepository}'s established pattern.
 */
public interface ZoneRepository extends Repository<Zone, UUID> {

    Zone save(Zone zone);

    Optional<Zone> findById(UUID id);

    List<Zone> findAll();
}
