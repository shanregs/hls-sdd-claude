package com.hls.training.calendar.internal;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SessionHistoryRepository extends JpaRepository<SessionHistory, UUID> {

    List<SessionHistory> findBySessionIdOrderByChangedAtAsc(UUID sessionId);

    List<SessionHistory> findBySessionIdInAndField(Collection<UUID> sessionIds, String field);
}
