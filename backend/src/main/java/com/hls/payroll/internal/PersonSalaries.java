package com.hls.payroll.internal;

import com.hls.payroll.internal.PersonSalary.Kind;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** The salary of Managers and staff in effect on a date, for many people in one query (spec 013a FR-003). */
@Component
class PersonSalaries {

    private final PersonSalaryRepository repository;

    PersonSalaries(PersonSalaryRepository repository) {
        this.repository = repository;
    }

    /** The row in effect on {@code date} for each person that has one; a person with none is absent. One query. */
    @Transactional(readOnly = true)
    Map<UUID, PersonSalary> inEffect(Kind kind, Collection<UUID> personIds, LocalDate date) {
        Set<UUID> ids = new HashSet<>(personIds);
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<UUID, List<PersonSalary>> byPerson = repository.findCandidates(kind, ids, date).stream()
                .collect(Collectors.groupingBy(PersonSalary::getPersonId));
        Map<UUID, PersonSalary> result = new HashMap<>();
        byPerson.forEach((id, rows) -> SalaryResolver.inEffect(rows, date).ifPresent(r -> result.put(id, r)));
        return result;
    }
}
