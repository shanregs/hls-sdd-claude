package com.hls.audit.loginhistory;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * Save and query only — no {@code update}/{@code delete} method exists on this repository
 * (FR-004, research.md §4). {@link JpaSpecificationExecutor} backs the optional filters
 * {@code AuditLoginHistoryController} exposes (userId/date-range/method/outcome).
 */
public interface LoginHistoryEntryRepository
        extends JpaRepository<LoginHistoryEntry, UUID>, JpaSpecificationExecutor<LoginHistoryEntry> {

    Optional<LoginHistoryEntry> findBySourceEventId(UUID sourceEventId);
}
