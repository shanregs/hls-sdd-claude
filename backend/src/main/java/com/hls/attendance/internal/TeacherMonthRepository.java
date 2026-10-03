package com.hls.attendance.internal;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TeacherMonthRepository extends JpaRepository<TeacherMonth, UUID> {

    Optional<TeacherMonth> findByTeacherIdAndYearMonth(UUID teacherId, String yearMonth);

    List<TeacherMonth> findByTeacherIdInAndYearMonth(Collection<UUID> teacherIds, String yearMonth);
}
