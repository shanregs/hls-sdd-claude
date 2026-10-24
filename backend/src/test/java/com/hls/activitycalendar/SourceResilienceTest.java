package com.hls.activitycalendar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.hls.activitycalendar.internal.ActivityCalendarService;
import com.hls.activitycalendar.internal.ActivityCalendarService.Caller;
import com.hls.activitycalendar.internal.ActivitySource;
import com.hls.activitycalendar.internal.CalendarEntry;
import com.hls.activitycalendar.internal.CalendarResponse;
import com.hls.activitycalendar.internal.PeopleDirectory;
import com.hls.activitycalendar.internal.PeopleDirectory.People;
import com.hls.activitycalendar.internal.SourceReport;
import com.hls.activitycalendar.internal.TrainingSource;
import com.hls.activitycalendar.internal.CalendarZoneResolver;
import com.hls.designation.api.BusinessDate;
import com.hls.identity.permissions.PermissionGuard;
import com.hls.identity.user.Role;
import com.hls.organization.api.ManagerQueries;
import com.hls.school.api.SchoolDirectory;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

/** Spec 035 T049 (SC-006): an unavailable or failing source gives a named note, never an empty or broken page. */
class SourceResilienceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 12);
    private static final UUID MANAGER = UUID.randomUUID();

    /** A source with fixed entries, or one that throws. */
    private record Stub(String group, boolean available, List<CalendarEntry> entries, boolean fails) implements ActivitySource {
        @Override
        public List<CalendarEntry> entries(LocalDate from, LocalDate to, UUID userIdOrNull) {
            if (fails) {
                throw new IllegalStateException("boom");
            }
            return entries;
        }
    }

    private static CalendarEntry entry(String kind) {
        return new CalendarEntry(kind, null, UUID.randomUUID(), TODAY.plusDays(2), null, null, MANAGER, Set.of(), "Somewhere", null,
                null, null, null, "PLANNED", null);
    }

    private static ActivityCalendarService service(ActivitySource... sources) {
        PeopleDirectory directory = mock(PeopleDirectory.class);
        when(directory.load(any())).thenReturn(new People(Map.of(MANAGER, "Asha"), Set.of(MANAGER), new HashMap<>()));
        CalendarZoneResolver zones = mock(CalendarZoneResolver.class);
        when(zones.resolve(any(), any())).thenReturn(mock(CalendarZoneResolver.Resolution.class));
        BusinessDate businessDate = mock(BusinessDate.class);
        when(businessDate.today()).thenReturn(TODAY);
        return new ActivityCalendarService(
                mock(ManagerQueries.class),
                mock(SchoolDirectory.class),
                List.of(sources),
                directory,
                zones,
                businessDate,
                mock(PermissionGuard.class),
                mock(PlatformTransactionManager.class));
    }

    private static final Caller DIRECTOR = new Caller(UUID.randomUUID(), Set.of(Role.DIRECTOR));

    private static SourceReport report(CalendarResponse r, String group) {
        return r.sources().stream().filter(s -> group.equals(s.group())).findFirst().orElseThrow();
    }

    @Test
    void noTrainingBeanGivesAnUnavailableNoteAndStaysComplete() {
        CalendarResponse r = service(
                        new Stub("DRIVES", true, List.of(entry("CAMPUS_DRIVE")), false),
                        new Stub("VISITS", true, List.of(entry("SCHOOL_VISIT")), false),
                        new Stub("TASKS", true, List.of(entry("TASK")), false),
                        new TrainingSource())
                .month(DIRECTOR, YearMonth.from(TODAY));
        SourceReport training = report(r, "TRAINING");
        assertThat(training.available()).isFalse();
        assertThat(training.read()).isFalse();
        assertThat(training.note()).isEqualTo("Training is not included yet");
        assertThat(r.partial()).isFalse();
        assertThat(r.items()).hasSize(3);
    }

    @Test
    void aStandInTrainingSourceShowsItsSessionsAsTraining() {
        CalendarResponse r = service(
                        new Stub("DRIVES", true, List.of(), false),
                        new Stub("VISITS", true, List.of(), false),
                        new Stub("TASKS", true, List.of(), false),
                        new Stub("TRAINING", true, List.of(entry("TRAINING")), false))
                .month(DIRECTOR, YearMonth.from(TODAY));
        assertThat(report(r, "TRAINING").read()).isTrue();
        assertThat(r.items()).hasSize(1);
        assertThat(r.items().get(0).kind()).isEqualTo("TRAINING");
        assertThat(r.items().get(0).people().get(0).userId()).isEqualTo(MANAGER);
    }

    @Test
    void aFailingTaskSourceLeavesDrivesAndVisitsUnchanged() {
        CalendarResponse r = service(
                        new Stub("DRIVES", true, List.of(entry("CAMPUS_DRIVE")), false),
                        new Stub("VISITS", true, List.of(entry("SCHOOL_VISIT")), false),
                        new Stub("TASKS", true, List.of(), true),
                        new TrainingSource())
                .month(DIRECTOR, YearMonth.from(TODAY));
        SourceReport tasks = report(r, "TASKS");
        assertThat(tasks.read()).isFalse();
        assertThat(tasks.note()).isEqualTo("Tasks could not be loaded");
        assertThat(r.partial()).isTrue();
        assertThat(r.items()).extracting(i -> i.kind()).containsExactlyInAnyOrder("CAMPUS_DRIVE", "SCHOOL_VISIT");
        assertThat(report(r, "DRIVES").read()).isTrue();
        assertThat(report(r, "VISITS").read()).isTrue();
    }

    @Test
    void allSourcesFailingStillAnswersWithEmptyItems() {
        CalendarResponse r = service(
                        new Stub("DRIVES", true, List.of(), true),
                        new Stub("VISITS", true, List.of(), true),
                        new Stub("TASKS", true, List.of(), true),
                        new TrainingSource())
                .month(DIRECTOR, YearMonth.from(TODAY));
        assertThat(r.items()).isEmpty();
        assertThat(r.partial()).isTrue();
        assertThat(r.sources()).hasSize(4).noneMatch(SourceReport::read);
    }
}
