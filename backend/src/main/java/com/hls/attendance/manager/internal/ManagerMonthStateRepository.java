package com.hls.attendance.manager.internal;

import com.hls.attendance.manager.internal.ManagerMonthState.Key;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ManagerMonthStateRepository extends JpaRepository<ManagerMonthState, Key> {

    /** The states of the given Managers for one month ({@code yyyy-MM}); a Manager with no row is open. */
    @Query("select s from ManagerMonthState s where s.id.managerId in :managerIds and s.id.yearMonth = :month")
    List<ManagerMonthState> findForMonth(
            @Param("managerIds") Collection<java.util.UUID> managerIds, @Param("month") String month);
}
