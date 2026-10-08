package com.hls.organization.internal;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ManagerDesignationRepository extends JpaRepository<ManagerDesignation, UUID> {

    List<ManagerDesignation> findByManagerId(UUID managerId);

    /** Every row of the given Managers in one query; callers pick the one in effect on a date in memory. */
    List<ManagerDesignation> findByManagerIdIn(Collection<UUID> managerIds);
}
