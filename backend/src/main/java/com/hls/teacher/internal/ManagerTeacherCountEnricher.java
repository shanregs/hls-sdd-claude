package com.hls.teacher.internal;

import com.hls.organization.api.ManagerQueries;
import com.hls.organization.api.ManagerViewEnricher;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Adds the number of Teachers currently placed at each Manager's Schools to Manager views. */
@Component
public class ManagerTeacherCountEnricher implements ManagerViewEnricher {

    private final ManagerQueries managerQueries;
    private final TeacherPlacementRepository placementRepository;
    private final Clock clock;

    public ManagerTeacherCountEnricher(
            @Lazy ManagerQueries managerQueries, TeacherPlacementRepository placementRepository, Clock clock) {
        this.managerQueries = managerQueries;
        this.placementRepository = placementRepository;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, Map<String, Object>> enrich(Collection<UUID> managerIds) {
        Map<UUID, Set<UUID>> schoolsByManager = managerQueries.currentSchoolIds(managerIds);
        LocalDate today = LocalDate.now(clock);
        Map<UUID, Map<String, Object>> result = new HashMap<>();
        for (UUID managerId : managerIds) {
            Set<UUID> schools = schoolsByManager.getOrDefault(managerId, Set.of());
            long count = schools.isEmpty()
                    ? 0
                    : new HashSet<>(placementRepository.teacherIdsAtSchoolsOn(schools, today)).size();
            result.put(managerId, Map.of("teacherCount", count));
        }
        return result;
    }
}
