package com.hls.content.internal;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface ContentHistoryRepository extends JpaRepository<ContentHistory, UUID> {

    List<ContentHistory> findByContentIdOrderByAt(UUID contentId);
}
