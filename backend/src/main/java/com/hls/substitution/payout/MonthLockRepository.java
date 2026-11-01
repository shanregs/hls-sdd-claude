package com.hls.substitution.payout;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MonthLockRepository extends JpaRepository<MonthLock, UUID> {

    Optional<MonthLock> findBySubstituteTeacherIdAndMonth(UUID substituteTeacherId, LocalDate month);

    @Query("select l from MonthLock l where l.substituteTeacherId in :teacherIds and l.month between :from and :to")
    List<MonthLock> of(
            @Param("teacherIds") Collection<UUID> teacherIds, @Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("select l from MonthLock l where l.month = :month and l.locked = true")
    List<MonthLock> lockedIn(@Param("month") LocalDate month);
}
