package com.hls.teacher.internal;

import com.hls.organization.api.ManagerQueries;
import com.hls.organization.api.ManagerViewEnricher;
import com.hls.teacher.api.TeacherPlacementSource;
import com.hls.designation.api.BusinessDate;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
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
    private final TeacherPlacementSource placementSource;
    private final BusinessDate businessDate;

    public ManagerTeacherCountEnricher(
            @Lazy ManagerQueries managerQueries, TeacherPlacementSource placementSource, BusinessDate businessDate) {
        this.managerQueries = managerQueries;
        this.placementSource = placementSource;
        this.businessDate = businessDate;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, Map<String, Object>> enrich(Collection<UUID> managerIds) {
        Map<UUID, Set<UUID>> schoolsByManager = managerQueries.currentSchoolIds(managerIds);
        LocalDate today = businessDate.today();
        Map<UUID, Map<String, Object>> result = new HashMap<>();
        for (UUID managerId : managerIds) {
            Set<UUID> schools = schoolsByManager.getOrDefault(managerId, Set.of());
            long count = schools.isEmpty()
                    ? 0
                    : placementSource.teacherIdsAtSchoolsOn(schools, today).size();
            result.put(managerId, Map.of("teacherCount", count));
        }
        return result;
    }
}
