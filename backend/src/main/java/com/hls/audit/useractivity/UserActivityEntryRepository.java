package com.hls.audit.useractivity;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Save and query only — no {@code update}/{@code delete} method (FR-004, research.md §4). */
public interface UserActivityEntryRepository
        extends JpaRepository<UserActivityEntry, UUID>, JpaSpecificationExecutor<UserActivityEntry> {

    Optional<UserActivityEntry> findBySourceEventId(UUID sourceEventId);
}
