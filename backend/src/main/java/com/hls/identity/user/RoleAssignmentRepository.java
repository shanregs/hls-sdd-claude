package com.hls.identity.user;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RoleAssignmentRepository extends JpaRepository<RoleAssignment, UUID> {

    List<RoleAssignment> findByUserId(UUID userId);

    /** Takes a pessimistic write lock so concurrent last-admin checks serialize (FR-011). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<RoleAssignment> findByRole(Role role);

    /** Ids of the users holding the role, read without a lock (for recipient lookups, not role changes). */
    @Query("select ra.userId from RoleAssignment ra where ra.role = :role")
    List<UUID> userIdsWithRole(@Param("role") Role role);

    void deleteByUserIdAndRole(UUID userId, Role role);
}
