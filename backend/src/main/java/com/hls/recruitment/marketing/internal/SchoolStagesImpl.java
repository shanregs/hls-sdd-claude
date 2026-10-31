package com.hls.recruitment.marketing.internal;

import com.hls.recruitment.api.SchoolStages;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Spec 014b A5: the effective stage of the prospect linked to each School, by the same resolver as the pipeline. */
@Service
@Transactional(readOnly = true)
class SchoolStagesImpl implements SchoolStages {

    private final ProspectRepository prospects;
    private final EffectiveStageResolver stages;

    SchoolStagesImpl(ProspectRepository prospects, EffectiveStageResolver stages) {
        this.prospects = prospects;
        this.stages = stages;
    }

    @Override
    public Map<UUID, String> stageOf(Collection<UUID> schoolIds) {
        if (schoolIds.isEmpty()) {
            return Map.of();
        }
        List<Prospect> linked = prospects.findBySchoolIdIn(schoolIds);
        Map<UUID, String> byProspect = stages.of(linked);
        Map<UUID, String> out = new HashMap<>();
        for (Prospect p : linked) {
            out.putIfAbsent(p.getSchoolId(), byProspect.get(p.getId()));
        }
        return out;
    }
}
