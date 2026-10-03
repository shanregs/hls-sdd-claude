package com.hls.audit.changehistory;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Save and query only — no {@code update}/{@code delete} method (FR-004, research.md §4). */
public interface ChangeHistoryEntryRepository
        extends JpaRepository<ChangeHistoryEntry, UUID>, JpaSpecificationExecutor<ChangeHistoryEntry> {

    Optional<ChangeHistoryEntry> findBySourceEventId(UUID sourceEventId);
}
