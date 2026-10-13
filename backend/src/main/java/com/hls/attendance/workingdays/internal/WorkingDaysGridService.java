package com.hls.attendance.workingdays.internal;

import com.hls.attendance.api.WorkingCalendar;
import com.hls.attendance.workingdays.internal.WorkingDaysViewFactory.RowView;
import com.hls.identity.user.Role;
import com.hls.school.api.InvalidInputException;
import com.hls.school.api.SchoolDirectory;
import com.hls.school.api.SchoolDirectory.SchoolInfo;
import com.hls.school.api.SchoolDirectory.ZoneInfo;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The grid of the School Working Days screen (spec 037 FR-015, FR-016): the rows of one year, filtered by School, Zone
 * and School name, sorted and paged. Filters and sorts that need School or Zone names are resolved in memory against
 * the School directory, because a year holds at most a few thousand rows; the number of queries does not depend on the
 * number of Schools.
 */
@Service
public class WorkingDaysGridService {

    public static final int MAX_PAGE_SIZE = 100;

    public record Page(List<RowView> items, long total, int page, int size) {}

    /** A School offered in the add and copy dialogs. */
    public record SchoolOption(UUID id, String name, UUID zoneId, String zoneName, boolean active) {}

    private final SchoolWorkingDaysRepository rows;
    private final SchoolDirectory schools;
    private final WorkingCalendar calendar;
    private final WorkingDaysScope scope;
    private final WorkingDaysViewFactory views;

    public WorkingDaysGridService(
            SchoolWorkingDaysRepository rows,
            SchoolDirectory schools,
            WorkingCalendar calendar,
            WorkingDaysScope scope,
            WorkingDaysViewFactory views) {
        this.rows = rows;
        this.schools = schools;
        this.calendar = calendar;
        this.scope = scope;
        this.views = views;
    }

    @Transactional(readOnly = true)
    public Page list(
            UUID userId,
            Set<Role> roles,
            Integer year,
            UUID schoolId,
            UUID zoneId,
            String search,
            String sort,
            int page,
            int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new InvalidInputException("Page size must be 1 to " + MAX_PAGE_SIZE + ".");
        }
        int wanted = year != null ? year : calendar.businessToday().getYear();
        Map<UUID, SchoolInfo> allowed = allowedSchools(userId, roles).stream()
                .filter(s -> schoolId == null || s.id().equals(schoolId))
                .filter(s -> zoneId == null || zoneId.equals(s.zoneId()))
                .filter(s -> search == null
                        || search.isBlank()
                        || s.name().toLowerCase(Locale.ROOT).contains(search.trim().toLowerCase(Locale.ROOT)))
                .collect(Collectors.toMap(SchoolInfo::id, s -> s));
        List<SchoolWorkingDaysRow> found = allowed.isEmpty()
                ? List.of()
                : rows.findBySchoolIdInAndYear(allowed.keySet(), wanted);
        Map<UUID, String> zoneNames = zoneNames(allowed.values());

        Comparator<SchoolWorkingDaysRow> order = comparator(sort, allowed, zoneNames);
        List<SchoolWorkingDaysRow> sorted = new ArrayList<>(found);
        sorted.sort(order);
        int from = Math.min(page * size, sorted.size());
        int to = Math.min(from + size, sorted.size());
        return new Page(views.views(sorted.subList(from, to)), sorted.size(), page, size);
    }

    /** The Schools the caller may add a figure for, active or not (rows of a deactivated School stay editable). */
    @Transactional(readOnly = true)
    public List<SchoolOption> options(UUID userId, Set<Role> roles) {
        List<SchoolInfo> list = allowedSchools(userId, roles);
        Map<UUID, String> zoneNames = zoneNames(list);
        return list.stream()
                .map(s -> new SchoolOption(
                        s.id(), s.name(), s.zoneId(), zoneNames.getOrDefault(s.zoneId(), ""), s.active()))
                .sorted(Comparator.comparing(o -> o.name().toLowerCase(Locale.ROOT)))
                .toList();
    }

    private List<SchoolInfo> allowedSchools(UUID userId, Set<Role> roles) {
        Set<UUID> visible = scope.visibleSchoolIds(userId, roles);
        List<SchoolInfo> all = schools.allSchools();
        if (visible == null) {
            return all;
        }
        return all.stream().filter(s -> visible.contains(s.id())).toList();
    }

    private Map<UUID, String> zoneNames(java.util.Collection<SchoolInfo> list) {
        Set<UUID> zoneIds = list.stream().map(SchoolInfo::zoneId).filter(Objects::nonNull).collect(Collectors.toCollection(HashSet::new));
        return schools.zones(zoneIds).stream().collect(Collectors.toMap(ZoneInfo::id, ZoneInfo::name));
    }

    private static Comparator<SchoolWorkingDaysRow> comparator(
            String sort, Map<UUID, SchoolInfo> schoolById, Map<UUID, String> zoneNames) {
        String key = sort == null || sort.isBlank() ? "school" : sort.split(",")[0].trim();
        boolean desc = sort != null && sort.toLowerCase(Locale.ROOT).endsWith(",desc");
        Comparator<SchoolWorkingDaysRow> byMonth = Comparator.comparingInt(SchoolWorkingDaysRow::getMonth);
        Comparator<SchoolWorkingDaysRow> bySchool = Comparator.comparing(
                (SchoolWorkingDaysRow r) -> nameOf(schoolById, r.getSchoolId()).toLowerCase(Locale.ROOT));
        Comparator<SchoolWorkingDaysRow> base = switch (key) {
            case "school" -> bySchool.thenComparing(byMonth);
            case "zone" -> Comparator.comparing((SchoolWorkingDaysRow r) -> zoneNames
                            .getOrDefault(zoneOf(schoolById, r.getSchoolId()), "")
                            .toLowerCase(Locale.ROOT))
                    .thenComparing(bySchool)
                    .thenComparing(byMonth);
            case "month" -> byMonth.thenComparing(bySchool);
            case "workingDays" -> Comparator.comparing(SchoolWorkingDaysRow::getWorkingDays)
                    .thenComparing(bySchool)
                    .thenComparing(byMonth);
            case "updatedAt" -> Comparator.comparing(SchoolWorkingDaysRow::getUpdatedAt)
                    .thenComparing(bySchool)
                    .thenComparing(byMonth);
            default -> throw new InvalidInputException(
                    "Sort must be school, zone, month, workingDays or updatedAt.");
        };
        return desc ? base.reversed() : base;
    }

    private static String nameOf(Map<UUID, SchoolInfo> schoolById, UUID id) {
        SchoolInfo info = schoolById.get(id);
        return info == null ? "" : info.name();
    }

    private static UUID zoneOf(Map<UUID, SchoolInfo> schoolById, UUID id) {
        SchoolInfo info = schoolById.get(id);
        return info == null ? null : info.zoneId();
    }
}
