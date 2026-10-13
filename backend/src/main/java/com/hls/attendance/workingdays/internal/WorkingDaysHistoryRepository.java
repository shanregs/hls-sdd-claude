package com.hls.attendance.workingdays.internal;

import java.util.List;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/** Insert and read only: the history is never updated or deleted. */
public interface WorkingDaysHistoryRepository extends Repository<WorkingDaysHistory, UUID> {

    WorkingDaysHistory save(WorkingDaysHistory entry);

    List<WorkingDaysHistory> findBySchoolIdAndYearAndMonthOrderByOccurredAtDesc(UUID schoolId, int year, int month);

    List<WorkingDaysHistory> findBySchoolIdAndYearAndActionOrderByOccurredAtDesc(
            UUID schoolId, int year, WorkingDaysHistory.Action action);
}
