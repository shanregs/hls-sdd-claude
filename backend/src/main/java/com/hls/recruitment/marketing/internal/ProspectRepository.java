package com.hls.recruitment.marketing.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface ProspectRepository extends JpaRepository<Prospect, UUID> {

    boolean existsByNameKey(String nameKey);

    Optional<Prospect> findByNameKey(String nameKey);

    Optional<Prospect> findFirstBySchoolId(UUID schoolId);

    List<Prospect> findByWonAtIsNotNullAndSchoolIdIsNotNull();

    List<Prospect> findByWonAtIsNotNullAndSchoolIdIsNull();
}
