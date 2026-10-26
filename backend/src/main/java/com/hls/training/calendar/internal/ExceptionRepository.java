package com.hls.training.calendar.internal;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface ExceptionRepository extends JpaRepository<TrainingException, UUID> {

    Optional<TrainingException> findBySessionIdAndPersonKindAndPersonId(UUID sessionId, String personKind, UUID personId);

    List<TrainingException> findBySessionId(UUID sessionId);

    List<TrainingException> findByPersonKindAndPersonId(String personKind, UUID personId);

    List<TrainingException> findByPersonKindAndPersonIdIn(String personKind, Collection<UUID> personIds);

    List<TrainingException> findByStatusIn(Collection<String> statuses);
}
