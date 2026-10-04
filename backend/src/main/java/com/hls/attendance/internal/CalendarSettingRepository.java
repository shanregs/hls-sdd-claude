package com.hls.attendance.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CalendarSettingRepository extends JpaRepository<CalendarSetting, UUID> {

    Optional<CalendarSetting> findBySchoolId(UUID schoolId);

    @Query("select c from CalendarSetting c where c.schoolId is null")
    Optional<CalendarSetting> findDefault();

    @Query("select c from CalendarSetting c where c.schoolId is not null")
    List<CalendarSetting> findOverrides();
}
