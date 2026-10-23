package com.hls.performance.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface ObservationAttachmentRepository extends JpaRepository<ObservationAttachment, UUID> {
    List<ObservationAttachment> findByObservationIdOrderByAddedAt(UUID observationId);

    Optional<ObservationAttachment> findByObservationIdAndFileId(UUID observationId, UUID fileId);
}
