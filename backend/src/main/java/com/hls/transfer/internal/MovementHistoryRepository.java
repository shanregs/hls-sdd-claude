package com.hls.transfer.internal;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface MovementHistoryRepository extends JpaRepository<MovementRequestHistory, UUID> {

    List<MovementRequestHistory> findByRequestIdOrderByAtAsc(UUID requestId);
}
