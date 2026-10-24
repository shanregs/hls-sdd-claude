package com.hls.activitycalendar.internal;

import com.hls.activitycalendar.internal.CalendarItem.Link;
import com.hls.activitycalendar.internal.CalendarItem.Person;
import com.hls.activitycalendar.internal.CalendarSummary.Counts;
import com.hls.activitycalendar.internal.CalendarSummary.ManagerCounts;
import com.hls.activitycalendar.internal.PeopleDirectory.People;
import com.hls.designation.api.BusinessDate;
import com.hls.identity.permissions.PermissionAction;
import com.hls.identity.permissions.PermissionGuard;
import com.hls.identity.permissions.PermissionModule;
import com.hls.identity.user.Role;
import com.hls.school.api.CallerContext;
import com.hls.organization.api.ManagerQueries;
import com.hls.school.api.InvalidInputException;
import com.hls.school.api.NotFoundException;
import com.hls.school.api.SchoolDirectory;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Merges the activity sources into one month of {@link CalendarItem}s with a summary and a source report. Read-only: it
 * calls the feeds, resolves names and Zones in bulk, and writes nothing. Nothing is cached, so a change made in an
 * owning screen shows on the next load.
 */
@Service
@Transactional(readOnly = true)
public class ActivityCalendarService {

    /** The longest range the calendar answers for. */
    public static final int MAX_CALENDAR_DAYS = 31;

    /** Who is asking: Admin and Director see everything, a Manager only their own items. */
    public record Caller(UUID userId, Set<Role> roles) {
        public boolean orgWide() {
            return CallerContext.isOrgWide(roles);
        }

        public boolean manager() {
            return !orgWide() && roles.contains(Role.MANAGER);
        }
    }

    /** The optional filters of the calendar; all combine, none widens a Manager's scope. */
    public record Filters(UUID managerId, UUID zoneId, String kind, String status) {
        public static final Filters NONE = new Filters(null, null, null, null);
    }

    public static final List<String> KINDS = List.of("CAMPUS_DRIVE", "SCHOOL_VISIT", "CALL", "MEETING", "TASK", "TRAINING");
    public static final List<String> STATUSES = List.of("PLANNED", "DONE", "MISSED", "CANCELLED");
    /** The longest range of the Needs attention list. */
    public static final int MAX_ATTENTION_DAYS = 93;

    /** A Missed or Rescheduled item with how late or how far moved. */
    public record AttentionEntry(CalendarItem item, List<String> reasons, Integer daysLate, Integer daysMoved) {}

    public record AttentionGroup(UUID userId, String name, int missed, int rescheduled, List<AttentionEntry> entries) {}

    public record AttentionResponse(LocalDate from, LocalDate to, boolean partial, List<SourceReport> sources, List<AttentionGroup> groups) {}

    public record NamedRef(UUID id, String name) {}

    public record KindChoice(String kind, boolean available) {}

    public record FilterChoices(List<NamedRef> managers, List<NamedRef> zones, List<KindChoice> kinds, List<String> statuses) {}

    /** What a load produced: the items in scope, how each source fared, and whether any source failed. */
    public record Loaded(List<SourceReport> sources, List<CalendarItem> items, boolean partial) {}

    private static final List<String> GROUPS =
            List.of(ActivitySource.DRIVES, ActivitySource.VISITS, ActivitySource.TASKS, ActivitySource.TRAINING);

    private final Map<String, ActivitySource> sources = new HashMap<>();
    private final PeopleDirectory directory;
    private final CalendarZoneResolver calendarZoneResolver;
    private final BusinessDate businessDate;
    private final PermissionGuard guard;
    private final TransactionTemplate isolated;
    private final ManagerQueries managerQueries;
    private final SchoolDirectory schools;

    public ActivityCalendarService(
            ManagerQueries managerQueries,
            SchoolDirectory schools,
            List<ActivitySource> sourceBeans,
            PeopleDirectory directory,
            CalendarZoneResolver calendarZoneResolver,
            BusinessDate businessDate,
            PermissionGuard guard,
            PlatformTransactionManager transactionManager) {
        for (ActivitySource source : sourceBeans) {
            sources.put(source.group(), source);
        }
        this.managerQueries = managerQueries;
        this.schools = schools;
        this.directory = directory;
        this.calendarZoneResolver = calendarZoneResolver;
        this.businessDate = businessDate;
        this.guard = guard;
        // each source runs in its own read-only transaction, so one failing source cannot poison the others
        this.isolated = new TransactionTemplate(transactionManager);
        this.isolated.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.isolated.setReadOnly(true);
    }

    /** The calendar of one month for the caller, without filters. */
    public CalendarResponse month(Caller caller, YearMonth month) {
        return month(caller, month, Filters.NONE);
    }

    /** The calendar of one month; the summary follows the filters. */
    public CalendarResponse month(Caller caller, YearMonth month, Filters filters) {
        validate(filters);
        checkScope(caller, filters);
        Loaded loaded = load(caller, month.atDay(1), month.atEndOfMonth(), MAX_CALENDAR_DAYS);
        List<CalendarItem> items = filter(loaded.items(), filters);
        return new CalendarResponse(
                month.toString(), businessDate.today(), loaded.partial(), loaded.sources(), summarize(items, caller.orgWide()), items);
    }

    /** Missed and rescheduled items of the period grouped by Manager, oldest first (FR-009, FR-010). */
    public AttentionResponse needsAttention(Caller caller, LocalDate from, LocalDate to, Filters filters) {
        validate(filters);
        checkScope(caller, filters);
        Loaded loaded = load(caller, from, to, MAX_ATTENTION_DAYS);
        // one entry per activity: a multi-date drive is judged on its last date
        Map<String, CalendarItem> representative = new LinkedHashMap<>();
        for (CalendarItem item : filter(loaded.items(), filters)) {
            representative.merge(item.kind() + ":" + item.activityId(), item, (x, y) -> y.date().isAfter(x.date()) ? y : x);
        }
        Map<UUID, List<AttentionEntry>> byManager = new HashMap<>();
        Map<UUID, String> names = new HashMap<>();
        for (CalendarItem item : representative.values()) {
            boolean missed = StatusRules.MISSED.equals(item.status());
            boolean moved = item.rescheduled()
                    && (StatusRules.PLANNED.equals(item.status()) || StatusRules.MISSED.equals(item.status()));
            if (!missed && !moved) {
                continue;
            }
            List<String> reasons = new ArrayList<>();
            if (missed) {
                reasons.add("MISSED");
            }
            if (moved) {
                reasons.add("RESCHEDULED");
            }
            AttentionEntry entry = new AttentionEntry(item, reasons, missed ? item.daysLate() : null, moved ? item.daysMoved() : null);
            for (Person p : item.people()) {
                if (!p.manager() || (caller.manager() && !p.userId().equals(caller.userId()))) {
                    continue;
                }
                if (filters.managerId() != null && !filters.managerId().equals(p.userId())) {
                    continue;
                }
                byManager.computeIfAbsent(p.userId(), k -> new ArrayList<>()).add(entry);
                names.put(p.userId(), p.name());
            }
        }
        List<AttentionGroup> groups = byManager.entrySet().stream()
                .map(en -> {
                    List<AttentionEntry> entries = en.getValue().stream()
                            .sorted(Comparator.comparing((AttentionEntry x) -> x.item().originalDate() != null ? x.item().originalDate() : x.item().date())
                                    .thenComparing(x -> x.item().key()))
                            .toList();
                    int missedCount = (int) entries.stream().filter(x -> x.reasons().contains("MISSED")).count();
                    int movedCount = (int) entries.stream().filter(x -> x.reasons().contains("RESCHEDULED")).count();
                    return new AttentionGroup(en.getKey(), names.get(en.getKey()), missedCount, movedCount, entries);
                })
                .sorted(Comparator.comparing(AttentionGroup::name).thenComparing(AttentionGroup::userId))
                .toList();
        return new AttentionResponse(from, to, loaded.partial(), loaded.sources(), groups);
    }

    /** The choices of the filter bar: Managers (only self for a Manager), Zones, kinds and which are available. */
    public FilterChoices filters(Caller caller) {
        if (!caller.orgWide() && !caller.manager()) {
            throw new AccessDeniedException("The Activity Calendar is for Admin, Director and Manager.");
        }
        List<NamedRef> managers = managerQueries.allManagers().stream()
                .filter(m -> caller.orgWide() || m.userId().equals(caller.userId()))
                .map(m -> new NamedRef(m.userId(), m.displayName()))
                .sorted(Comparator.comparing(NamedRef::name, String.CASE_INSENSITIVE_ORDER).thenComparing(NamedRef::id))
                .toList();
        List<NamedRef> zones = schools.allZones().stream().map(z -> new NamedRef(z.id(), z.name())).toList();
        List<KindChoice> kinds = KINDS.stream()
                .map(k -> new KindChoice(k, !"TRAINING".equals(k) || trainingAvailable()))
                .toList();
        return new FilterChoices(managers, zones, kinds, STATUSES);
    }

    private boolean trainingAvailable() {
        ActivitySource training = sources.get(ActivitySource.TRAINING);
        return training != null && training.available();
    }

    private static void validate(Filters f) {
        if (f.kind() != null && !KINDS.contains(f.kind())) {
            throw new InvalidInputException("The kind must be one of " + String.join(", ", KINDS) + ".");
        }
        if (f.status() != null && !STATUSES.contains(f.status())) {
            throw new InvalidInputException("The status must be one of " + String.join(", ", STATUSES) + ".");
        }
    }

    /** A Manager may name only themselves; another Manager answers 404 as if it did not exist. */
    private static void checkScope(Caller caller, Filters f) {
        if (caller.manager() && f.managerId() != null && !f.managerId().equals(caller.userId())) {
            throw new NotFoundException("Manager not found.");
        }
    }

    private static List<CalendarItem> filter(List<CalendarItem> items, Filters f) {
        return items.stream()
                .filter(i -> f.managerId() == null || i.people().stream().anyMatch(p -> p.manager() && p.userId().equals(f.managerId())))
                .filter(i -> f.zoneId() == null || f.zoneId().equals(i.zoneId()))
                .filter(i -> f.kind() == null || f.kind().equals(i.kind()))
                .filter(i -> f.status() == null || f.status().equals(i.status()))
                .toList();
    }

    /** All items in the range that the caller may see, plus the source report. */
    public Loaded load(Caller caller, LocalDate from, LocalDate to, int maxDays) {
        if (to.isBefore(from)) {
            throw new InvalidInputException("The end date cannot be before the start date.");
        }
        if (ChronoUnit.DAYS.between(from, to) + 1 > maxDays) {
            throw new InvalidInputException("Choose a period of at most " + maxDays + " days.");
        }
        if (!caller.orgWide() && !caller.manager()) {
            throw new AccessDeniedException("The Activity Calendar is for Admin, Director and Manager.");
        }
        UUID scopeUser = caller.manager() ? caller.userId() : null;

        List<SourceReport> reports = new ArrayList<>();
        List<CalendarEntry> entries = new ArrayList<>();
        Map<CalendarEntry, String> groupOf = new HashMap<>();
        boolean partial = false;
        for (String group : GROUPS) {
            ActivitySource source = sources.get(group);
            if (source == null || !source.available()) {
                reports.add(new SourceReport(group, false, false, titleOf(group) + " is not included yet"));
                continue;
            }
            try {
                List<CalendarEntry> found = isolated.execute(status -> source.entries(from, to, scopeUser));
                for (CalendarEntry e : found == null ? List.<CalendarEntry>of() : found) {
                    entries.add(e);
                    groupOf.put(e, group);
                }
                reports.add(new SourceReport(group, true, true, null));
            } catch (RuntimeException failure) {
                partial = true;
                reports.add(new SourceReport(group, true, false, titleOf(group) + " could not be loaded"));
            }
        }
        return new Loaded(reports, build(caller, entries, groupOf), partial);
    }

    private List<CalendarItem> build(Caller caller, List<CalendarEntry> entries, Map<CalendarEntry, String> groupOf) {
        if (entries.isEmpty()) {
            return List.of();
        }
        Set<UUID> userIds = new HashSet<>();
        for (CalendarEntry e : entries) {
            if (e.ownerUserId() != null) {
                userIds.add(e.ownerUserId());
            }
            userIds.addAll(e.attendeeUserIds());
        }
        People people = directory.load(userIds);
        CalendarZoneResolver.Resolution zones = calendarZoneResolver.resolve(entries, people);
        LocalDate today = businessDate.today();
        Map<String, Boolean> mayView = new HashMap<>();

        List<CalendarItem> items = new ArrayList<>();
        for (CalendarEntry e : entries) {
            List<Person> persons = personsOf(e, people);
            if (caller.manager() && persons.stream().noneMatch(p -> p.userId().equals(caller.userId()))) {
                continue;
            }
            StatusRules.Result result = StatusRules.derive(e.nativeStatus(), e.date(), e.lastDate(), e.completedOn(), today);
            ZoneRef zone = zones.zoneOf(e);
            items.add(new CalendarItem(
                    e.kind() + ":" + e.id() + ":" + e.date(),
                    e.id(),
                    e.kind(),
                    e.subKind(),
                    e.date(),
                    result.status(),
                    result.doneLate(),
                    e.completedOn(),
                    StatusRules.rescheduled(e.originalDate(), e.date()),
                    e.originalDate(),
                    StatusRules.daysMoved(e.originalDate(), e.date()),
                    result.daysLate(),
                    persons,
                    zone == null ? null : zone.id(),
                    zone == null ? null : zone.name(),
                    e.place(),
                    linkFor(groupOf.get(e), caller, mayView)));
        }
        items.sort(Comparator.comparing(CalendarItem::date).thenComparing(CalendarItem::kind).thenComparing(CalendarItem::key));
        return items;
    }

    private static List<Person> personsOf(CalendarEntry e, People people) {
        List<Person> out = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();
        if (e.ownerUserId() != null && seen.add(e.ownerUserId())) {
            out.add(person(e.ownerUserId(), people));
        }
        e.attendeeUserIds().stream()
                .filter(people::isManager)
                .filter(seen::add)
                .map(id -> person(id, people))
                .sorted(Comparator.comparing(Person::name).thenComparing(Person::userId))
                .forEach(out::add);
        return out;
    }

    private static Person person(UUID userId, People people) {
        ZoneRef zone = people.zoneOf(userId);
        return new Person(userId, people.name(userId), zone == null ? null : zone.name(), people.isManager(userId));
    }

    /** The owning screen, offered only when the caller may view it (FR-020 and Principle IV). */
    private Link linkFor(String group, Caller caller, Map<String, Boolean> mayView) {
        PermissionModule module;
        Link link;
        switch (group) {
            case ActivitySource.DRIVES -> {
                module = PermissionModule.RECRUITMENT;
                link = new Link("RECRUITMENT", "/recruitment/drives");
            }
            case ActivitySource.VISITS -> {
                module = PermissionModule.MARKETING;
                link = new Link("MARKETING", "/marketing/calendar");
            }
            case ActivitySource.TASKS -> {
                module = PermissionModule.TASKS;
                link = new Link("TASKS", "/operations/tasks");
            }
            default -> {
                return null;
            }
        }
        boolean allowed = mayView.computeIfAbsent(group, g -> guard.isGranted(caller.roles(), module, PermissionAction.VIEW));
        return allowed ? link : null;
    }

    /** Counts over distinct activities; see {@link CalendarSummary}. */
    static CalendarSummary summarize(List<CalendarItem> items, boolean withManagers) {
        // one representative per activity: the item with the latest date (a drive is judged on its last date)
        Map<String, CalendarItem> representative = new LinkedHashMap<>();
        for (CalendarItem item : items) {
            representative.merge(item.kind() + ":" + item.activityId(), item, (a, b) -> b.date().isAfter(a.date()) ? b : a);
        }
        Tally total = new Tally();
        Map<String, Tally> byKind = new TreeMap<>();
        Map<UUID, Tally> byManager = new HashMap<>();
        Map<UUID, String> managerNames = new HashMap<>();
        for (CalendarItem item : representative.values()) {
            total.add(item);
            byKind.computeIfAbsent(item.kind(), k -> new Tally()).add(item);
            if (withManagers) {
                for (Person p : item.people()) {
                    if (p.manager()) {
                        byManager.computeIfAbsent(p.userId(), k -> new Tally()).add(item);
                        managerNames.put(p.userId(), p.name());
                    }
                }
            }
        }
        Map<String, Counts> kinds = new LinkedHashMap<>();
        byKind.forEach((k, t) -> kinds.put(k, t.counts()));
        List<ManagerCounts> managers = null;
        if (withManagers) {
            managers = byManager.entrySet().stream()
                    .map(en -> {
                        Counts c = en.getValue().counts();
                        return new ManagerCounts(
                                en.getKey(), managerNames.get(en.getKey()), c.planned(), c.done(), c.doneLate(), c.open(), c.missed(),
                                c.cancelled(), c.rescheduled());
                    })
                    .sorted(Comparator.comparing(ManagerCounts::name).thenComparing(ManagerCounts::userId))
                    .toList();
        }
        Counts c = total.counts();
        return new CalendarSummary(
                c.planned(), c.done(), c.doneLate(), c.open(), c.missed(), c.cancelled(), c.rescheduled(), kinds, managers);
    }

    private static String titleOf(String group) {
        return switch (group) {
            case ActivitySource.DRIVES -> "Drives";
            case ActivitySource.VISITS -> "Visits";
            case ActivitySource.TASKS -> "Tasks";
            default -> "Training";
        };
    }

    /** Running counts of one group of items. */
    private static final class Tally {
        private int done;
        private int doneLate;
        private int open;
        private int missed;
        private int cancelled;
        private int rescheduled;

        void add(CalendarItem item) {
            switch (item.status()) {
                case StatusRules.DONE -> {
                    done++;
                    if (item.doneLate()) {
                        doneLate++;
                    }
                }
                case StatusRules.MISSED -> missed++;
                case StatusRules.CANCELLED -> cancelled++;
                default -> open++;
            }
            // a moved item counts once however many times it moved; a cancelled one is no longer a moved plan
            if (item.rescheduled() && !StatusRules.CANCELLED.equals(item.status())) {
                rescheduled++;
            }
        }

        Counts counts() {
            return new Counts(done + open + missed, done, doneLate, open, missed, cancelled, rescheduled);
        }
    }
}
