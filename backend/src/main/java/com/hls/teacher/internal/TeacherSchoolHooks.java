package com.hls.teacher.internal;

import com.hls.school.api.ConflictException;
import com.hls.school.api.SchoolDeactivationGuard;
import com.hls.school.api.SchoolViewEnricher;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** What {@code school} asks of {@code teacher}: a deactivation veto and the Teacher count on School views. */
@Component
public class TeacherSchoolHooks implements SchoolDeactivationGuard, SchoolViewEnricher {

    private final TeacherPlacementRepository placementRepository;
    private final Clock clock;

    public TeacherSchoolHooks(TeacherPlacementRepository placementRepository, Clock clock) {
        this.placementRepository = placementRepository;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public void checkDeactivate(UUID schoolId) {
        if (placementRepository.existsCurrentOrFuture(schoolId, LocalDate.now(clock))) {
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
        Map<UUID, Long> counts = new HashMap<>();
        for (Object[] row : placementRepository.countBySchoolOn(schoolIds, LocalDate.now(clock))) {
            counts.put((UUID) row[0], ((Number) row[1]).longValue());
        }
        Map<UUID, Map<String, Object>> result = new HashMap<>();
        for (UUID id : schoolIds) {
            result.put(id, Map.of("teacherCount", counts.getOrDefault(id, 0L)));
        }
        return result;
    }
}
