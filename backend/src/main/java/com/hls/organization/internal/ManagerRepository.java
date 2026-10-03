package com.hls.organization.internal;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ManagerRepository extends JpaRepository<Manager, UUID> {

    Optional<Manager> findByUserId(UUID userId);

    /**
     * Locks the given Manager rows (in id order, so concurrent callers cannot deadlock) for the
     * rest of the transaction: every operation that changes or checks a Manager's assignments
     * takes this first (spec 005 FR-009, research.md section 5).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Manager m where m.id in :ids order by m.id")
    List<Manager> lockAll(@Param("ids") Collection<UUID> ids);
}
