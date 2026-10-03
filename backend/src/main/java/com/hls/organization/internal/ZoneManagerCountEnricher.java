package com.hls.organization.internal;

import com.hls.school.api.ZoneViewEnricher;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Adds the number of Managers currently assigned to each Zone. */
@Component
public class ZoneManagerCountEnricher implements ZoneViewEnricher {

    private final ZoneManagerAssignmentRepository zoneAssignments;

    public ZoneManagerCountEnricher(ZoneManagerAssignmentRepository zoneAssignments) {
        this.zoneAssignments = zoneAssignments;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, Map<String, Object>> enrich(Collection<UUID> zoneIds) {
        if (zoneIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, Long> counts = zoneAssignments.findByZoneIdInAndEndsOnIsNull(zoneIds).stream()
                .collect(Collectors.groupingBy(ZoneManagerAssignment::getZoneId, Collectors.counting()));
        Map<UUID, Map<String, Object>> result = new HashMap<>();
        for (UUID id : zoneIds) {
            result.put(id, Map.of("managerCount", counts.getOrDefault(id, 0L)));
        }
        return result;
    }
}
