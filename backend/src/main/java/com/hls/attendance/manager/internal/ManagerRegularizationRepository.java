package com.hls.attendance.manager.internal;

import com.hls.attendance.manager.internal.ManagerRegularization.Status;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ManagerRegularizationRepository extends JpaRepository<ManagerRegularization, UUID> {

    List<ManagerRegularization> findByManagerIdOrderByRequestedAtDesc(UUID managerId);

    List<ManagerRegularization> findByStatusOrderByRequestedAtAsc(Status status);

    List<ManagerRegularization> findByManagerIdAndStatusOrderByRequestedAtAsc(UUID managerId, Status status);

    Optional<ManagerRegularization> findByManagerIdAndDayDateAndStatus(UUID managerId, LocalDate dayDate, Status status);

    /** The pending requests whose day falls in the range, for the month lock. */
    List<ManagerRegularization> findByStatusAndDayDateBetween(Status status, LocalDate from, LocalDate to);
}
