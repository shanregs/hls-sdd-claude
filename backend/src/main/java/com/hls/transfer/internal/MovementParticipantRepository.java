package com.hls.transfer.internal;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface MovementParticipantRepository extends JpaRepository<MovementParticipant, UUID> {

    List<MovementParticipant> findByRequestId(UUID requestId);

    boolean existsByTeacherIdAndOpenTrue(UUID teacherId);

    List<MovementParticipant> findByTeacherIdInAndOpenTrue(Collection<UUID> teacherIds);
}
