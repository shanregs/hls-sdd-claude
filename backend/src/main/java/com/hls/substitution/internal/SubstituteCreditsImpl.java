package com.hls.substitution.internal;

import com.hls.substitution.api.SubstituteCredit;
import com.hls.substitution.api.SubstituteCredits;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Spec 017a FR-015: the live credits are the assigned requirements, read through this one interface. */
@Service
@Transactional(readOnly = true)
class SubstituteCreditsImpl implements SubstituteCredits {

    private final SubstitutionRepository requirements;

    SubstituteCreditsImpl(SubstitutionRepository requirements) {
        this.requirements = requirements;
    }

    @Override
    public List<SubstituteCredit> creditsOf(UUID substituteTeacherId, YearMonth month) {
        return creditsOf(List.of(substituteTeacherId), month.atDay(1), month.atEndOfMonth())
                .getOrDefault(substituteTeacherId, List.of());
    }

    @Override
    public Map<UUID, List<SubstituteCredit>> creditsOf(Collection<UUID> substituteTeacherIds, LocalDate from, LocalDate to) {
        if (substituteTeacherIds.isEmpty() || from.isAfter(to)) {
            return Map.of();
        }
        Map<UUID, List<SubstituteCredit>> result = new LinkedHashMap<>();
        for (SubstitutionRequirement r : requirements.credits(substituteTeacherIds, from, to)) {
            result.computeIfAbsent(r.getSubstituteTeacherId(), k -> new ArrayList<>())
                    .add(new SubstituteCredit(
                            r.getId(),
                            r.getSubstituteTeacherId(),
                            r.getTeacherId(),
                            r.getSchoolId(),
                            r.getDutyDate(),
                            r.getDayValue()));
        }
        return result;
    }
}
