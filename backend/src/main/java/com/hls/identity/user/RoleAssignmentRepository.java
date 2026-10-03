package com.hls.identity.user;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface RoleAssignmentRepository extends JpaRepository<RoleAssignment, UUID> {

    List<RoleAssignment> findByUserId(UUID userId);

    /** Takes a pessimistic write lock so concurrent last-admin checks serialize (FR-011). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<RoleAssignment> findByRole(Role role);

    void deleteByUserIdAndRole(UUID userId, Role role);
}
