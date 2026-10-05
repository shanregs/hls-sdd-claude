package com.hls.recruitment.marketing.internal;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface ActivityDateHistoryRepository extends JpaRepository<ActivityDateHistory, UUID> {

    List<ActivityDateHistory> findByActivityIdOrderByNewDateAsc(UUID activityId);
}
