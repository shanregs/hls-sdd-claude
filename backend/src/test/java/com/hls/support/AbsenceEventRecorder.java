package com.hls.support;

import com.hls.attendance.api.TeacherAbsenceRecorded;
import com.hls.attendance.api.TeacherAbsenceWithdrawn;
import java.time.LocalDate;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Test-only listener that keeps the attendance absence events (spec 008 A2). The tests call the server over HTTP, so the
 * events are published on server threads and cannot be seen with Spring's per-thread ApplicationEvents.
 */
@Component
public class AbsenceEventRecorder {

    private final Queue<TeacherAbsenceRecorded> recorded = new ConcurrentLinkedQueue<>();
    private final Queue<TeacherAbsenceWithdrawn> withdrawn = new ConcurrentLinkedQueue<>();

    @EventListener
    void on(TeacherAbsenceRecorded event) {
        recorded.add(event);
    }

    @EventListener
    void on(TeacherAbsenceWithdrawn event) {
        withdrawn.add(event);
    }

    public List<TeacherAbsenceRecorded> recorded(UUID teacher, LocalDate date) {
        return recorded.stream().filter(e -> e.teacherId().equals(teacher) && e.date().equals(date)).toList();
    }

    public List<TeacherAbsenceWithdrawn> withdrawn(UUID teacher, LocalDate date) {
        return withdrawn.stream().filter(e -> e.teacherId().equals(teacher) && e.date().equals(date)).toList();
    }
}
