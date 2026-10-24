package com.hls.training.calendar.internal;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface AttendanceEntryRepository extends JpaRepository<AttendanceEntry, UUID> {

    List<AttendanceEntry> findBySessionId(UUID sessionId);

    List<AttendanceEntry> findBySessionIdIn(Collection<UUID> sessionIds);

    Optional<AttendanceEntry> findBySessionIdAndPersonKindAndPersonId(UUID sessionId, String personKind, UUID personId);

    List<AttendanceEntry> findByPersonKindAndPersonId(String personKind, UUID personId);

    List<AttendanceEntry> findByPersonKindAndPersonIdAndOutcome(String personKind, UUID personId, String outcome);

    List<AttendanceEntry> findBySyncState(String syncState);

    boolean existsBySessionId(UUID sessionId);
}
