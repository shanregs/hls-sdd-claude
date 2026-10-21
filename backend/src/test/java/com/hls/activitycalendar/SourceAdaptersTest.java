package com.hls.activitycalendar;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.activitycalendar.internal.DriveSource;
import com.hls.activitycalendar.internal.TaskSource;
import com.hls.activitycalendar.internal.VisitSource;
import com.hls.recruitment.api.ActivityEntry;
import com.hls.recruitment.api.DriveActivities;
import com.hls.recruitment.api.MarketingActivities;
import com.hls.recruitment.api.PlannedActivity;
import com.hls.tasks.api.TaskActivities;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Spec 035 T026: the three adapters map kinds, keep attendees and copy only the feed fields. */
class SourceAdaptersTest {

    private static final LocalDate FROM = LocalDate.of(2026, 10, 1);
    private static final LocalDate TO = LocalDate.of(2026, 10, 31);
    private static final UUID OWNER = UUID.randomUUID();
    private static final UUID ATTENDEE = UUID.randomUUID();

    private static ActivityEntry feed(String kind, LocalDate date, LocalDate original, String status, LocalDate completed) {
        return new ActivityEntry(
                kind, UUID.randomUUID(), OWNER, Set.of(ATTENDEE), date, original, "Place", null, UUID.randomUUID(), null, null, status, completed);
    }

    private static com.hls.activitycalendar.internal.ActivitySource visits(List<ActivityEntry> entries) {
        MarketingActivities feed = new MarketingActivities() {
            @Override
            public List<PlannedActivity> plannedBetween(LocalDate from, LocalDate to, UUID ownerUserId) {
                throw new AssertionError("the calendar must not use plannedBetween");
            }

            @Override
            public List<ActivityEntry> activitiesBetween(LocalDate from, LocalDate to, UUID userId) {
                return entries;
            }
        };
        return new VisitSource(feed);
    }

    @Test
    void visitKindsMapToSchoolVisitCallAndMeeting() {
        var source = visits(List.of(
                feed("SCHOOL_VISIT", FROM, null, "PLANNED", null),
                feed("SCHOOL_CALL", FROM, null, "PLANNED", null),
                feed("SCHOOL_PROPOSAL_MEETING", FROM, null, "PLANNED", null),
                feed("SCHOOL_FOLLOW_UP", FROM, null, "COMPLETED", FROM)));

        var out = source.entries(FROM, TO, null);

        assertThat(out).extracting(e -> e.kind()).containsExactly("SCHOOL_VISIT", "CALL", "MEETING", "MEETING");
        assertThat(source.group()).isEqualTo("VISITS");
        assertThat(source.available()).isTrue();
        assertThat(out.get(0).attendeeUserIds()).containsExactly(ATTENDEE);
        assertThat(out.get(3).nativeStatus()).isEqualTo("COMPLETED");
        assertThat(out.get(3).completedOn()).isEqualTo(FROM);
    }

    @Test
    void aDriveIsJudgedOnItsLastDateEvenWhenThatDateIsOutsideTheMonth() {
        UUID drive = UUID.randomUUID();
        var d1 = new ActivityEntry("CAMPUS_DRIVE", drive, OWNER, Set.of(), TO, null, "College, Chennai", null, null, null, null, "PLANNED", null);
        var d2 = new ActivityEntry(
                "CAMPUS_DRIVE", drive, OWNER, Set.of(), TO.plusDays(2), TO, "College, Chennai", null, null, null, null, "PLANNED", null);
        DriveActivities feed = new DriveActivities() {
            @Override
            public List<PlannedActivity> plannedBetween(LocalDate from, LocalDate to, UUID ownerUserId) {
                throw new AssertionError("the calendar must not use plannedBetween");
            }

            @Override
            public List<ActivityEntry> activitiesBetween(LocalDate from, LocalDate to, UUID userId) {
                return List.of(d1, d2);
            }
        };

        var out = new DriveSource(feed).entries(FROM, TO, null);

        assertThat(out).hasSize(1);
        assertThat(out.get(0).kind()).isEqualTo("CAMPUS_DRIVE");
        assertThat(out.get(0).date()).isEqualTo(TO);
        assertThat(out.get(0).lastDate()).isEqualTo(TO.plusDays(2));
        assertThat(out.get(0).place()).isEqualTo("College, Chennai");
    }

    @Test
    void aTaskBecomesKindTaskWithTheTypeKeptAndNoAttendees() {
        UUID id = UUID.randomUUID();
        TaskActivities feed = new TaskActivities() {
            @Override
            public List<PlannedActivity> plannedBetween(LocalDate from, LocalDate to, UUID ownerUserId) {
                throw new AssertionError("the calendar must not use plannedBetween");
            }

            @Override
            public List<ActivityEntry> activitiesBetween(LocalDate from, LocalDate to, UUID ownerUserId) {
                return List.of(new ActivityEntry(
                        "FOLLOW_UP", id, OWNER, FROM, FROM.minusDays(3), null, "SCHOOL", id, "VERIFIED", FROM));
            }
        };

        var out = new TaskSource(feed).entries(FROM, TO, OWNER);

        assertThat(out).singleElement().satisfies(e -> {
            assertThat(e.kind()).isEqualTo("TASK");
            assertThat(e.subKind()).isEqualTo("FOLLOW_UP");
            assertThat(e.ownerUserId()).isEqualTo(OWNER);
            assertThat(e.attendeeUserIds()).isEmpty();
            assertThat(e.originalDate()).isEqualTo(FROM.minusDays(3));
            assertThat(e.linkedType()).isEqualTo("SCHOOL");
            assertThat(e.nativeStatus()).isEqualTo("VERIFIED");
            assertThat(e.completedOn()).isEqualTo(FROM);
        });
    }

    @Test
    void theCalendarEntryCarriesNoFreeText() {
        assertThat(com.hls.activitycalendar.internal.CalendarEntry.class.getRecordComponents())
                .extracting(java.lang.reflect.RecordComponent::getName)
                .doesNotContain("notes", "outcome", "title", "description", "evidence", "attachments");
    }
}
