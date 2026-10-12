package com.hls.tasks.internal;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

interface TaskRepository extends JpaRepository<Task, UUID>, JpaSpecificationExecutor<Task> {

    Optional<Task> findFirstBySourceAndSourceKeyAndStatusIn(String source, String sourceKey, Collection<TaskStatus> statuses);

    /** Open tasks whose due date is before the given day: the candidates of the daily check. */
    List<Task> findByStatusInAndDueOnBefore(Collection<TaskStatus> statuses, LocalDate day);

    List<Task> findByParentTaskIdOrderByCreatedAt(UUID parentTaskId);

    List<Task> findByDueOnBetweenAndStatusNot(LocalDate from, LocalDate to, TaskStatus status);

    List<Task> findByAssigneeUserIdAndDueOnBetweenAndStatusNot(UUID assignee, LocalDate from, LocalDate to, TaskStatus status);
}
