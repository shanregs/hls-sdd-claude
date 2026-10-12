package com.hls.tasks.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface TaskEvidenceRepository extends JpaRepository<TaskEvidence, UUID> {

    List<TaskEvidence> findByTaskIdOrderByAddedAtAsc(UUID taskId);

    long countByTaskId(UUID taskId);

    Optional<TaskEvidence> findByFileId(UUID fileId);
}
