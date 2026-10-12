package com.hls.attendance.manager.internal;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ManagerDayRepository extends JpaRepository<ManagerDay, UUID> {

    Optional<ManagerDay> findByManagerIdAndDayDate(UUID managerId, LocalDate dayDate);

    List<ManagerDay> findByManagerIdAndDayDateBetweenOrderByDayDate(UUID managerId, LocalDate from, LocalDate to);

    /** One query for the grid: every listed Manager's records in the range. */
    List<ManagerDay> findByManagerIdInAndDayDateBetween(Collection<UUID> managerIds, LocalDate from, LocalDate to);

    /** The records of a date, for the alert job and the on-leave list. */
    List<ManagerDay> findByDayDate(LocalDate dayDate);

    List<ManagerDay> findByLeaveRequestId(UUID leaveRequestId);
}
