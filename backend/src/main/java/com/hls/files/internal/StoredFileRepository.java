package com.hls.files.internal;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface StoredFileRepository extends JpaRepository<StoredFile, UUID> {

    List<StoredFile> findByOwnerTypeAndOwnerIdAndRemovedAtIsNullOrderByAddedAt(String ownerType, UUID ownerId);
}
