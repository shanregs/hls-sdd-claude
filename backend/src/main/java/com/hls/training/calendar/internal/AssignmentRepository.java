package com.hls.training.calendar.internal;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface AssignmentRepository extends JpaRepository<Assignment, UUID> {

    List<Assignment> findBySessionId(UUID sessionId);

    List<Assignment> findBySessionIdIn(Collection<UUID> sessionIds);

    Optional<Assignment> findBySessionIdAndPersonKindAndPersonId(UUID sessionId, String personKind, UUID personId);

    List<Assignment> findByPersonKindAndPersonId(String personKind, UUID personId);

    List<Assignment> findByPersonKindAndPersonIdInAndStatus(String personKind, Collection<UUID> personIds, String status);

    List<Assignment> findBySessionIdAndStatus(UUID sessionId, String status);
}
