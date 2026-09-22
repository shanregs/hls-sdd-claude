package com.hls.audit.internal;

import java.util.List;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/**
 * Deliberately narrower than {@code JpaRepository}/{@code CrudRepository}:
 * Constitution Principle I and FR-005/FR-006 require this table be
 * append-only, so no {@code delete}/{@code deleteById}/update method is
 * exposed here at all — there is nothing to accidentally call. Mirrors
 * {@code identity.internal.AuthAuditEntryRepository}'s existing pattern
 * exactly (research.md §4).
 */
public interface AuditEntryRepository extends Repository<AuditEntry, UUID> {

    AuditEntry save(AuditEntry entry);

    List<AuditEntry> findByEntityTypeAndEntityIdOrderBySequenceNoAsc(String entityType, String entityId);
}
