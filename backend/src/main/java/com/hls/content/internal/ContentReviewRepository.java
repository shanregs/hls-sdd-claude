package com.hls.content.internal;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface ContentReviewRepository extends JpaRepository<ContentReview, UUID> {

    List<ContentReview> findByContentIdOrderByDecidedAt(UUID contentId);
}
