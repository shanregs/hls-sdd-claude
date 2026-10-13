package com.hls.attendance.workingdays.internal;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SchoolWorkingDaysRepository extends JpaRepository<SchoolWorkingDaysRow, UUID> {

    List<SchoolWorkingDaysRow> findByYear(int year);

    List<SchoolWorkingDaysRow> findBySchoolIdInAndYear(Collection<UUID> schoolIds, int year);

    List<SchoolWorkingDaysRow> findBySchoolIdAndYear(UUID schoolId, int year);

    Optional<SchoolWorkingDaysRow> findBySchoolIdAndYearAndMonth(UUID schoolId, int year, int month);

    /** Rows for the Schools whose month index ({@code year * 12 + month - 1}) lies between the two bounds. */
    @Query("select r from SchoolWorkingDaysRow r where r.schoolId in :schoolIds"
            + " and (r.year * 12 + r.month - 1) between :fromIndex and :toIndex")
    List<SchoolWorkingDaysRow> findInRange(
            @Param("schoolIds") Collection<UUID> schoolIds,
            @Param("fromIndex") int fromIndex,
            @Param("toIndex") int toIndex);
}
