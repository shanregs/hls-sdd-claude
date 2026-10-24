package com.hls.transfer.internal;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface MovementAssignmentRepository extends JpaRepository<MovementAssignment, UUID> {

    List<MovementAssignment> findByRequestId(UUID requestId);

    List<MovementAssignment> findByAssignmentIdIn(Collection<UUID> assignmentIds);
}
