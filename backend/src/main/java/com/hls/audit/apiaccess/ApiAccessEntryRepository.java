package com.hls.audit.apiaccess;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * Save and query only: no {@code update}/{@code delete} method exists (spec 003 FR-004 applies to
 * every audit repository). {@link JpaSpecificationExecutor} backs the filters of the API Access screen.
 */
public interface ApiAccessEntryRepository
        extends JpaRepository<ApiAccessEntry, UUID>, JpaSpecificationExecutor<ApiAccessEntry> {

    Optional<ApiAccessEntry> findBySourceEventId(UUID sourceEventId);
}
