package com.hls.substitution.internal;

import com.hls.substitution.api.SubstitutionCover;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Spec 014b A3: the day values by School and status, from one grouped query. */
@Service
@Transactional(readOnly = true)
class SubstitutionCoverImpl implements SubstitutionCover {

    private final SubstitutionRepository requirements;

    SubstitutionCoverImpl(SubstitutionRepository requirements) {
        this.requirements = requirements;
    }

    @Override
    public Map<UUID, SchoolCover> coverBySchool(Collection<UUID> schoolIds, LocalDate from, LocalDate to) {
        if (schoolIds.isEmpty() || from.isAfter(to)) {
            return Map.of();
        }
        Map<UUID, BigDecimal[]> sums = new LinkedHashMap<>();
        for (Object[] row : requirements.daysBySchoolAndStatus(schoolIds, from, to)) {
            BigDecimal[] s = sums.computeIfAbsent((UUID) row[0], k -> new BigDecimal[] {BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO});
            BigDecimal days = (BigDecimal) row[2];
            switch ((RequirementStatus) row[1]) {
                case ASSIGNED -> s[0] = s[0].add(days);
                case MANAGER_COVER -> s[1] = s[1].add(days);
                case OPEN -> s[2] = s[2].add(days);
                default -> {
                    // cancelled requirements are not counted
                }
            }
        }
        Map<UUID, SchoolCover> out = new LinkedHashMap<>();
        sums.forEach((id, s) -> out.put(id, new SchoolCover(id, s[0], s[1], s[2])));
        return out;
    }
}
