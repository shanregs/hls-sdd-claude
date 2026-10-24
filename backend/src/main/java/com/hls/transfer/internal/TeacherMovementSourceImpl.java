package com.hls.transfer.internal;

import com.hls.teacher.api.TeacherDirectory;
import com.hls.teacher.api.TeacherDirectory.PlacementSpan;
import com.hls.teacher.api.TeacherDirectory.TeacherInfo;
import com.hls.teacher.api.TeacherMovementSource;
import com.hls.teacher.api.TeacherPlacementSource;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Serves the Transferred and Replaced filters of the Teacher list (spec 027 research.md section 10). */
@Component
class TeacherMovementSourceImpl implements TeacherMovementSource {

    private static final LocalDate LONG_AGO = LocalDate.of(2000, 1, 1);
    private static final LocalDate FOREVER = LocalDate.of(9999, 12, 31);

    private final MovementRequestRepository requests;
    private final MovementQueries queries;
    private final TeacherDirectory teachers;
    private final TeacherPlacementSource placements;

    TeacherMovementSourceImpl(
            MovementRequestRepository requests,
            MovementQueries queries,
            TeacherDirectory teachers,
            TeacherPlacementSource placements) {
        this.requests = requests;
        this.queries = queries;
        this.teachers = teachers;
        this.placements = placements;
    }

    @Override
    @Transactional(readOnly = true)
    public Set<UUID> transferredTeacherIds() {
        Set<UUID> ids = new HashSet<>();
        for (MovementRequest r : requests.findByStatus("APPROVED")) {
            if ("TRANSFER".equals(r.getKind()) && inForce(r)) {
                ids.add(r.getTeacherId());
            }
        }
        List<UUID> everyone = teachers.allTeachers().stream().map(TeacherInfo::id).toList();
        if (!everyone.isEmpty()) {
            Map<UUID, Set<UUID>> schoolsByTeacher = new HashMap<>();
            for (PlacementSpan span : placements.spansOverlapping(everyone, LONG_AGO, FOREVER)) {
                schoolsByTeacher.computeIfAbsent(span.teacherId(), k -> new HashSet<>()).add(span.schoolId());
            }
            schoolsByTeacher.forEach((teacher, schools) -> {
                if (schools.size() > 1) {
                    ids.add(teacher);
                }
            });
        }
        return ids;
    }

    @Override
    @Transactional(readOnly = true)
    public Set<UUID> replacedTeacherIds() {
        Set<UUID> ids = new HashSet<>();
        for (MovementRequest r : requests.findByStatus("APPROVED")) {
            if ("REPLACEMENT".equals(r.getKind()) && inForce(r)) {
                ids.add(r.getTeacherId());
            }
        }
        return ids;
    }

    private boolean inForce(MovementRequest r) {
        String shown = queries.shown(r).status();
        return "APPROVED".equals(shown) || "APPLIED".equals(shown);
    }
}
