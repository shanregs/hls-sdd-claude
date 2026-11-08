package com.hls.teacher.internal;

import com.hls.school.api.ConflictException;
import com.hls.school.api.SchoolDeactivationGuard;
import com.hls.school.api.SchoolViewEnricher;
import com.hls.teacher.api.TeacherPlacementSource;
import com.hls.designation.api.BusinessDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** What {@code school} asks of {@code teacher}: a deactivation veto and the Teacher count on School views. */
@Component
public class TeacherSchoolHooks implements SchoolDeactivationGuard, SchoolViewEnricher {

    private final TeacherPlacementSource placementSource;
    private final BusinessDate businessDate;

    public TeacherSchoolHooks(TeacherPlacementSource placementSource, BusinessDate businessDate) {
        this.placementSource = placementSource;
        this.businessDate = businessDate;
    }

    @Override
    @Transactional(readOnly = true)
    public void checkDeactivate(UUID schoolId) {
        if (placementSource.hasCurrentOrFutureAssignment(schoolId, businessDate.today())) {
            throw new ConflictException(
                    "This School still has Teachers placed in it or scheduled to arrive. Move them first.");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, Map<String, Object>> enrich(Collection<UUID> schoolIds) {
        if (schoolIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, Long> counts = placementSource.teacherCountsBySchool(schoolIds, businessDate.today());
        Map<UUID, Map<String, Object>> result = new HashMap<>();
        for (UUID id : schoolIds) {
            result.put(id, Map.of("teacherCount", counts.getOrDefault(id, 0L)));
        }
        return result;
    }
}
