package com.hls.attendance.manager.internal;

import com.hls.attendance.manager.internal.MissedAlert.Key;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MissedAlertRepository extends JpaRepository<MissedAlert, Key> {

    /** The Managers already alerted on a date. */
    @Query("select a.id.managerId from MissedAlert a where a.id.alertDate = :date and a.id.managerId in :managerIds")
    List<UUID> alertedOn(@Param("date") LocalDate date, @Param("managerIds") Collection<UUID> managerIds);
}
