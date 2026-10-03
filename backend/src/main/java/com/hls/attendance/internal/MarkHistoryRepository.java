package com.hls.attendance.internal;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MarkHistoryRepository extends JpaRepository<MarkHistoryEntry, UUID> {

    List<MarkHistoryEntry> findByTeacherIdAndMarkDateOrderBySetAtDesc(UUID teacherId, LocalDate markDate);
}
