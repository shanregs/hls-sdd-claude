package com.hls.teacher.internal;

import com.hls.designation.api.DesignationDirectory.Kind;
import com.hls.designation.api.HolderCounter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Tells the designation list how many Teachers hold each designation and how many have none. */
@Component
@Transactional(readOnly = true)
class TeacherHolderCounter implements HolderCounter {

    private final TeacherRepository teachers;

    TeacherHolderCounter(TeacherRepository teachers) {
        this.teachers = teachers;
    }

    @Override
    public Kind kind() {
        return Kind.TEACHER;
    }

    @Override
    public Map<UUID, Long> holdersByDesignation() {
        Map<UUID, Long> result = new HashMap<>();
        for (Object[] row : teachers.countByDesignation()) {
            result.put((UUID) row[0], (Long) row[1]);
        }
        return result;
    }

    @Override
    public long missingDesignation() {
        return teachers.countByDesignationIdIsNull();
    }

    @Override
    public long missingJoiningDate() {
        return 0;
    }

    @Override
    public long missingExitDate() {
        return 0;
    }
}
