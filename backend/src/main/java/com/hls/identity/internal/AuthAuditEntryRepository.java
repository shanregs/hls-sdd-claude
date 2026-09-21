package com.hls.identity.internal;

import org.springframework.data.repository.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Deliberately narrower than {@code JpaRepository}: Constitution Principle I requires
 * this table be append-only, so no {@code delete}/{@code deleteById} method is exposed
 * here at all — there is nothing to accidentally call.
 */
public interface AuthAuditEntryRepository extends Repository<AuthAuditEntry, UUID> {

    AuthAuditEntry save(AuthAuditEntry entry);

    Optional<AuthAuditEntry> findById(UUID id);

    List<AuthAuditEntry> findByActorUserId(UUID actorUserId);
}
