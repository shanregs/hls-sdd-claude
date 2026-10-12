package com.hls.attendance.manager.internal;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ManagerMonthEventRepository extends JpaRepository<ManagerMonthEvent, UUID> {

    List<ManagerMonthEvent> findByManagerIdAndYearMonthOrderByOccurredAtAsc(UUID managerId, String yearMonth);
}
