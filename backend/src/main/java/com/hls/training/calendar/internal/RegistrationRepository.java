package com.hls.training.calendar.internal;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface RegistrationRepository extends JpaRepository<Registration, UUID> {

    List<Registration> findBySessionId(UUID sessionId);

    List<Registration> findBySessionIdIn(Collection<UUID> sessionIds);

    Optional<Registration> findBySessionIdAndTeacherId(UUID sessionId, UUID teacherId);

    long countBySessionIdAndStatus(UUID sessionId, String status);

    List<Registration> findByTeacherId(UUID teacherId);

    List<Registration> findByTeacherIdInAndStatus(Collection<UUID> teacherIds, String status);
}
