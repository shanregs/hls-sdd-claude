package com.hls.recruitment.marketing.internal;

import com.hls.recruitment.api.ProspectZones;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Bulk read of prospect Zones for the Activity Calendar (spec 035); no scope is applied here. */
@Service
class ProspectZonesImpl implements ProspectZones {

    private final ProspectRepository prospects;

    ProspectZonesImpl(ProspectRepository prospects) {
        this.prospects = prospects;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, UUID> zonesOf(Collection<UUID> prospectIds) {
        Map<UUID, UUID> out = new HashMap<>();
        if (prospectIds == null || prospectIds.isEmpty()) {
            return out;
        }
        for (Prospect p : prospects.findAllById(prospectIds)) {
            out.put(p.getId(), p.getZoneId());
        }
        return out;
    }
}
