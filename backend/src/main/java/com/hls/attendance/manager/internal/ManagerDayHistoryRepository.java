package com.hls.attendance.manager.internal;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Save and query only: the table refuses any update or delete. */
public interface ManagerDayHistoryRepository extends JpaRepository<ManagerDayHistory, UUID> {

    List<ManagerDayHistory> findByDayIdOrderByChangedAtAsc(UUID dayId);

    List<ManagerDayHistory> findByDayIdInOrderByChangedAtAsc(Collection<UUID> dayIds);
}
