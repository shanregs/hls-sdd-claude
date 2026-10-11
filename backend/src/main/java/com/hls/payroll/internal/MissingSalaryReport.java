package com.hls.payroll.internal;

import com.hls.designation.api.DesignationDirectory.Kind;
import com.hls.payroll.internal.PersonDirectory.DesignationRef;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * For each designation, how many of the people who hold it have no salary of their own today (spec 013a FR-013,
 * research.md sections 9 and 11). A person's pay is always their own: a Teacher's record (spec 005) or, for a Manager,
 * their history in this spec. The designation's amount is only a suggested default, so it never clears a gap. A fixed
 * number of queries however many designations there are.
 */
@Component
class MissingSalaryReport {

    static final String NOTE = "People holding this designation with no salary of their own";

    /** People left without a salary; the flag is on when there is at least one. */
    record Missing(boolean flag, long people, String note) {}

    private final PersonDirectory people;
    private final PersonSalaries personSalaries;

    MissingSalaryReport(PersonDirectory people, PersonSalaries personSalaries) {
        this.people = people;
        this.personSalaries = personSalaries;
    }

    @Transactional(readOnly = true)
    Map<UUID, Missing> compute(Collection<DesignationRef> designations, LocalDate today) {
        List<UUID> managerDesignations = designations.stream()
                .filter(d -> d.kind() == Kind.MANAGER)
                .map(DesignationRef::id)
                .toList();
        List<UUID> teacherDesignations = designations.stream()
                .filter(d -> d.kind() == Kind.TEACHER)
                .map(DesignationRef::id)
                .toList();

        Map<UUID, List<UUID>> managerHolders = people.managerHolders(managerDesignations);
        Set<UUID> allManagers = new HashSet<>();
        managerHolders.values().forEach(allManagers::addAll);
        Set<UUID> managersWithSalary =
                personSalaries.inEffect(PersonSalary.Kind.MANAGER, allManagers, today).keySet();

        Map<UUID, List<UUID>> teacherHolders = people.teacherHolders(teacherDesignations);
        Set<UUID> allTeachers = new HashSet<>();
        teacherHolders.values().forEach(allTeachers::addAll);
        Set<UUID> teachersWithSalary = people.teacherSalariesOn(allTeachers, today).keySet();

        Map<UUID, Missing> result = new HashMap<>();
        for (DesignationRef d : designations) {
            List<UUID> holders = d.kind() == Kind.MANAGER
                    ? managerHolders.getOrDefault(d.id(), List.of())
                    : teacherHolders.getOrDefault(d.id(), List.of());
            Set<UUID> withSalary = d.kind() == Kind.MANAGER ? managersWithSalary : teachersWithSalary;
            long count = holders.stream().filter(id -> !withSalary.contains(id)).count();
            result.put(d.id(), new Missing(count > 0, count, NOTE));
        }
        return result;
    }
}
