package com.hls.performance.internal;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface HistoryRepository extends JpaRepository<PerformanceHistory, UUID> {
    List<PerformanceHistory> findByRecordTypeAndRecordIdOrderByAt(String recordType, UUID recordId);

    List<PerformanceHistory> findByTeacherIdOrderByAtDesc(UUID teacherId);
}
