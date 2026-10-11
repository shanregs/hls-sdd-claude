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
 * For each designation, how many people it leaves without a salary today (spec 013a FR-013, research.md section 9).
 * A Manager's salary is their designation's, so the gap is the Managers holding a designation that has none. A
 * Teacher's salary is their own record (the designation's amount is only a default), so the gap is the Teachers
 * holding the designation who have no recorded salary of their own. A fixed number of queries however many
 * designations there are.
 */
@Component
class MissingSalaryReport {

    static final String MANAGER_NOTE = "Managers holding this designation have no salary.";
    static final String TEACHER_NOTE = "Teachers with no salary of their own";

    /** People left without a salary; the flag is on when there is at least one. */
    record Missing(boolean flag, long people, String note) {}

    private final PersonDirectory people;

    MissingSalaryReport(PersonDirectory people) {
        this.people = people;
    }

    /**
     * @param designationsWithSalary the designations that have a salary in effect on {@code today}
     */
    @Transactional(readOnly = true)
    Map<UUID, Missing> compute(
            Collection<DesignationRef> designations, Set<UUID> designationsWithSalary, LocalDate today) {
        List<UUID> managerDesignations = designations.stream()
                .filter(d -> d.kind() == Kind.MANAGER && !designationsWithSalary.contains(d.id()))
                .map(DesignationRef::id)
                .toList();
        List<UUID> teacherDesignations = designations.stream()
                .filter(d -> d.kind() == Kind.TEACHER)
                .map(DesignationRef::id)
                .toList();

        Map<UUID, Long> managerGaps = people.managerHolders(managerDesignations);
        Map<UUID, List<UUID>> teacherHolders = people.teacherHolders(teacherDesignations);
        Set<UUID> allTeachers = new HashSet<>();
        teacherHolders.values().forEach(allTeachers::addAll);
        Set<UUID> withOwnSalary = people.teacherSalariesOn(allTeachers, today).keySet();

        Map<UUID, Missing> result = new HashMap<>();
        for (DesignationRef d : designations) {
            long count;
            String note;
            if (d.kind() == Kind.MANAGER) {
                count = designationsWithSalary.contains(d.id()) ? 0 : managerGaps.getOrDefault(d.id(), 0L);
                note = MANAGER_NOTE;
            } else {
                count = teacherHolders.getOrDefault(d.id(), List.of()).stream()
                        .filter(id -> !withOwnSalary.contains(id))
                        .count();
                note = TEACHER_NOTE;
            }
            result.put(d.id(), new Missing(count > 0, count, note));
        }
        return result;
    }
}
