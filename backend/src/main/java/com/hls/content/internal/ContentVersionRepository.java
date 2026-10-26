package com.hls.content.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface ContentVersionRepository extends JpaRepository<ContentVersion, UUID> {

    List<ContentVersion> findByContentIdOrderByNumber(UUID contentId);

    Optional<ContentVersion> findByContentIdAndNumber(UUID contentId, int number);

    boolean existsByContentIdAndSubmittedByUserId(UUID contentId, UUID submittedByUserId);

    List<ContentVersion> findBySubmittedByUserId(UUID submittedByUserId);
}
