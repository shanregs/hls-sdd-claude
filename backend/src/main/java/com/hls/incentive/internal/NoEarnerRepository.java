package com.hls.incentive.internal;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface NoEarnerRepository extends JpaRepository<NoEarnerRecord, UUID> {

    Optional<NoEarnerRecord> findBySchoolId(UUID schoolId);

    long countByResolvedAtIsNull();
}
