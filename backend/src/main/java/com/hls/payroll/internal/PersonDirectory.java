package com.hls.payroll.internal;

import com.hls.designation.api.DesignationDirectory.Kind;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * What payroll needs to know about designations, Managers, Teachers and staff. The rest of the module depends on this
 * port, not on the types of the modules behind it (spec 013a T011), so those can change without touching the rules.
 * Every bulk method is one query however many ids it is given.
 */
public interface PersonDirectory {

    record DesignationRef(UUID id, String name, Kind kind, boolean retired) {}

    /** A Manager's joining and exit dates; either may be null while not recorded or open-ended. */
    record ManagerDates(LocalDate joiningDate, LocalDate exitDate) {}

    record ManagerSummary(UUID id, String name, boolean active) {}

    record TeacherSummary(UUID id, String name, String status) {}

    /** A user who is paid but is neither a Manager nor a Teacher: holds the Admin or Director role. */
    record StaffUser(UUID id, String name, boolean active, List<String> roles) {}

    /** An ACTIVE placement of a Teacher; {@code to} is inclusive and null while open-ended. */
    record Placement(UUID teacherId, UUID schoolId, LocalDate from, LocalDate to) {}

    /** A recorded salary and the date it took effect. */
    record RecordedSalary(BigDecimal amount, LocalDate effectiveOn) {}

    /** Every designation, by kind then name; {@code kind} null means both kinds. */
    List<DesignationRef> designations(Kind kind, boolean includeRetired);

    Optional<DesignationRef> designation(UUID designationId);

    /** The designations by id; an unknown id is absent. */
    Map<UUID, DesignationRef> designations(Collection<UUID> designationIds);

    /** The designation each Manager held on the date; a Manager with none is absent. */
    Map<UUID, UUID> managerDesignationsOn(Collection<UUID> managerIds, LocalDate date);

    /** The Managers that exist, with their dates. */
    Map<UUID, ManagerDates> managers(Collection<UUID> managerIds);

    List<ManagerSummary> allManagers();

    /** For each designation, the ids of the Managers who hold it today; absent means none. */
    Map<UUID, List<UUID>> managerHolders(Collection<UUID> designationIds);

    /** The Teachers that exist. */
    Set<UUID> existingTeachers(Collection<UUID> teacherIds);

    List<TeacherSummary> allTeachers();

    /** The current designation of each Teacher; a Teacher with none is absent. */
    Map<UUID, UUID> teacherDesignations(Collection<UUID> teacherIds);

    /** For each designation, the ids of the Teachers holding it; absent means none. */
    Map<UUID, List<UUID>> teacherHolders(Collection<UUID> designationIds);

    /** Each Teacher's own recorded salary on the date; a Teacher with none is absent. */
    Map<UUID, RecordedSalary> teacherSalariesOn(Collection<UUID> teacherIds, LocalDate date);

    /** The staff users that exist among the given ids. */
    Map<UUID, StaffUser> staff(Collection<UUID> userIds);

    /** Every staff user (Admin or Director role), by name. */
    List<StaffUser> allStaff();

    /** ACTIVE placements overlapping {@code from..to}, oldest first. */
    List<Placement> placements(Collection<UUID> teacherIds, LocalDate from, LocalDate to);
}
