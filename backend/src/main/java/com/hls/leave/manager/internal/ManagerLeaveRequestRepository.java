package com.hls.leave.manager.internal;

import com.hls.leave.manager.internal.ManagerLeaveRequest.Status;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ManagerLeaveRequestRepository extends JpaRepository<ManagerLeaveRequest, UUID> {

    List<ManagerLeaveRequest> findByManagerIdOrderByRequestedAtDesc(UUID managerId);

    List<ManagerLeaveRequest> findByStatusOrderByRequestedAtAsc(Status status);

    List<ManagerLeaveRequest> findByManagerIdAndStatusOrderByRequestedAtAsc(UUID managerId, Status status);

    List<ManagerLeaveRequest> findByManagerIdOrderByRequestedAtAsc(UUID managerId);

    /** The manager's requests in these statuses that overlap the range, for the overlap check. */
    List<ManagerLeaveRequest> findByManagerIdAndStatusInAndFirstDateLessThanEqualAndLastDateGreaterThanEqual(
            UUID managerId, Collection<Status> statuses, LocalDate lastDate, LocalDate firstDate);
}
