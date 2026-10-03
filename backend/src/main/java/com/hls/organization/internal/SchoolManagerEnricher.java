package com.hls.organization.internal;

import com.hls.organization.api.ManagerQueries.ManagerRef;
import com.hls.school.api.SchoolViewEnricher;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Adds the School's current Manager, and a needs-a-Manager flag, to School views. */
@Component
public class SchoolManagerEnricher implements SchoolViewEnricher {

    private final ManagerService managerService;

    public SchoolManagerEnricher(ManagerService managerService) {
        this.managerService = managerService;
    }

    @Override
    public Map<UUID, Map<String, Object>> enrich(Collection<UUID> schoolIds) {
        Map<UUID, ManagerRef> managers = managerService.managersOfSchools(schoolIds);
        Map<UUID, Map<String, Object>> result = new HashMap<>();
        for (UUID id : schoolIds) {
            ManagerRef ref = managers.get(id);
            Map<String, Object> attrs = new HashMap<>();
            attrs.put("manager", ref == null ? null : Map.of("id", ref.id(), "displayName", ref.displayName()));
            attrs.put("needsManager", ref == null || !ref.active());
            result.put(id, attrs);
        }
        return result;
    }
}
