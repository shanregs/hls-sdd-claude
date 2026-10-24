package com.hls.leave.manager.internal;

import com.hls.leave.manager.internal.ManagerLeaveRequest.Status;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ManagerLeaveRequestRepository extends JpaRepository<ManagerLeaveRequest, UUID> {

    List<ManagerLeaveRequest> findByManagerIdOrderByRequestedAtDesc(UUID managerId);

    List<ManagerLeaveRequest> findByStatusOrderByRequestedAtAsc(Status status);

    List<ManagerLeaveRequest> findByManagerIdAndStatusOrderByRequestedAtAsc(UUID managerId, Status status);

    List<ManagerLeaveRequest> findByManagerIdOrderByRequestedAtAsc(UUID managerId);

    /** The requests in a status that touch the range (first on or before its end, last on or after its start). */
    List<ManagerLeaveRequest> findByStatusAndFirstDateLessThanEqualAndLastDateGreaterThanEqual(
            Status status, LocalDate lastDate, LocalDate firstDate);

    /** The manager's requests in these statuses that overlap the range, for the overlap check. */
    List<ManagerLeaveRequest> findByManagerIdAndStatusInAndFirstDateLessThanEqualAndLastDateGreaterThanEqual(
            UUID managerId, Collection<Status> statuses, LocalDate lastDate, LocalDate firstDate);

    /**
     * Takes a transaction-scoped Postgres advisory lock on the key, so two simultaneous submissions by the same
     * Manager run their overlap check and insert one after the other. The lock is released at commit or rollback.
     * The result is the empty text of the void function and carries no meaning.
     */
    @Query(value = "select cast(pg_advisory_xact_lock(hashtextextended(:key, 0)) as text)", nativeQuery = true)
    String lockForSubmission(@Param("key") String key);
}
