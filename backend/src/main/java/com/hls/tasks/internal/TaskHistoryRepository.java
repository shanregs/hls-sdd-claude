package com.hls.tasks.internal;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface TaskHistoryRepository extends JpaRepository<TaskHistory, UUID> {

    List<TaskHistory> findByTaskIdOrderByAtAsc(UUID taskId);
}
