package com.hls.attendance.internal;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TeacherMonthEventRepository extends JpaRepository<TeacherMonthEvent, UUID> {

    List<TeacherMonthEvent> findByTeacherIdAndYearMonthOrderByOccurredAt(UUID teacherId, String yearMonth);
}
