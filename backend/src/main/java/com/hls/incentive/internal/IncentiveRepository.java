package com.hls.incentive.internal;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface IncentiveRepository extends JpaRepository<Incentive, UUID>, JpaSpecificationExecutor<Incentive> {

    Optional<Incentive> findBySchoolId(UUID schoolId);

    List<Incentive> findBySchoolIdIn(Collection<UUID> schoolIds);

    List<Incentive> findByStatus(IncentiveStatus status);

    long countByStatus(IncentiveStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Incentive i where i.id = :id")
    Optional<Incentive> lockById(@Param("id") UUID id);
}
