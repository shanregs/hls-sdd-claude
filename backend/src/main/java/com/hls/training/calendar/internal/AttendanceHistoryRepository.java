package com.hls.training.calendar.internal;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface AttendanceHistoryRepository extends JpaRepository<AttendanceHistory, UUID> {

    List<AttendanceHistory> findByEntryIdInOrderByChangedAtAsc(Collection<UUID> entryIds);
}
