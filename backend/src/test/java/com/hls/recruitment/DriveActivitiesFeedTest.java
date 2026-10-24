package com.hls.recruitment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hls.designation.api.BusinessDate;
import com.hls.identity.user.Role;
import com.hls.recruitment.api.ActivityEntry;
import com.hls.recruitment.api.DriveActivities;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;

/** Spec 035 T014: the second feed method of campus drives, held_on and the paired date changes. */
class DriveActivitiesFeedTest extends RecruitmentTestBase {

    @Autowired
    DriveActivities feed;

    @Autowired
    BusinessDate businessDate;

    private List<ActivityEntry> entries(UUID drive, UUID user) {
        return feed.activitiesBetween(today.minusDays(40), today.plusDays(60), user).stream()
                .filter(e -> e.id().equals(drive))
                .sorted(java.util.Comparator.comparing(ActivityEntry::date))
                .toList();
    }

    private Resp update(String token, UUID drive, List<UUID> interviewers, LocalDate... dates) {
        Map<String, Object> detail = get(BASE + "/drives/" + drive, token).map();
        UUID college = UUID.fromString(((Map<?, ?>) detail.get("college")).get("id").toString());
        Map<String, Object> body = new HashMap<>(driveBody(college, interviewers, dates));
        body.put("version", detail.get("version"));
        return put(BASE + "/drives/" + drive, token, body);
    }

    @Test
    void everyStatusIsReturnedAndHeldOnIsStoredByMarkHeld() {
        String admin = admin();
        UUID college = college(admin);
        UUID planned = drive(admin, college, List.of(), today.plusDays(2));
        UUID held = drive(admin, college, List.of(), today.plusDays(3));
        UUID cancelled = drive(admin, college, List.of(), today.plusDays(4));
        assertThat(post(BASE + "/drives/" + held + "/status", admin, Map.of("status", "HELD")).status()).isEqualTo(200);
        assertThat(post(BASE + "/drives/" + cancelled + "/status", admin, Map.of("status", "CANCELLED", "reason", "Exams")).status())
                .isEqualTo(200);

        assertThat(entries(planned, null)).singleElement().satisfies(e -> {
            assertThat(e.status()).isEqualTo("PLANNED");
            assertThat(e.kind()).isEqualTo("CAMPUS_DRIVE");
            assertThat(e.completedOn()).isNull();
            assertThat(e.zoneId()).isNull();
            assertThat(e.place()).contains(", Chennai");
        });
        assertThat(entries(held, null)).singleElement().satisfies(e -> {
            assertThat(e.status()).isEqualTo("HELD");
            assertThat(e.completedOn()).isEqualTo(businessDate.today());
        });
        assertThat(entries(cancelled, null)).singleElement().satisfies(e -> assertThat(e.status()).isEqualTo("CANCELLED"));
    }

    @Test
    void aDriveHeldBeforeTheAmendmentHasNoCompletionDate() {
        String admin = admin();
        UUID drive = drive(admin, college(admin), List.of(), today.plusDays(2));
        post(BASE + "/drives/" + drive + "/status", admin, Map.of("status", "HELD"));
        jdbc.update("update campus_drive set held_on = null where id = ?", drive);

        assertThat(entries(drive, null)).singleElement().satisfies(e -> assertThat(e.completedOn()).isNull());
    }

    @Test
    void completedOnOfAMultiDateDriveIsHeldOnOnTheLastDateAndTheEarlierOfTheTwoBefore() {
        String admin = admin();
        UUID drive = drive(admin, college(admin), List.of(), today.minusDays(5), today.plusDays(2), today.plusDays(4));
        post(BASE + "/drives/" + drive + "/status", admin, Map.of("status", "HELD"));
        LocalDate held = businessDate.today();

        List<ActivityEntry> rows = entries(drive, null);

        assertThat(rows).hasSize(3);
        assertThat(rows.get(0).completedOn()).isEqualTo(today.minusDays(5).isBefore(held) ? today.minusDays(5) : held);
        assertThat(rows.get(2).completedOn()).isEqualTo(held);
    }

    @Test
    void anEqualSizeDateSwapWritesPairedChangesAndSetsTheOriginalDate() {
        String admin = admin();
        UUID college = college(admin);
        UUID drive = drive(admin, college, List.of(), today.plusDays(2), today.plusDays(3));
        assertThat(entries(drive, null)).allSatisfy(e -> assertThat(e.originalDate()).isNull());

        Resp moved = update(admin, drive, List.of(), today.plusDays(5), today.plusDays(6));
        assertThat(moved.status()).as(moved.body()).isEqualTo(200);

        assertThat(jdbc.queryForObject("select count(*) from drive_date_change where drive_id = ?", Integer.class, drive)).isEqualTo(2);
        List<ActivityEntry> rows = entries(drive, null);
        assertThat(rows.get(0).date()).isEqualTo(today.plusDays(5));
        assertThat(rows.get(0).originalDate()).isEqualTo(today.plusDays(2));
        assertThat(rows.get(1).date()).isEqualTo(today.plusDays(6));
        assertThat(rows.get(1).originalDate()).isEqualTo(today.plusDays(3));
    }

    @Test
    void aDateAddedOrRemovedAloneWritesNothing() {
        String admin = admin();
        UUID drive = drive(admin, college(admin), List.of(), today.plusDays(2));

        assertThat(update(admin, drive, List.of(), today.plusDays(2), today.plusDays(3)).status()).isEqualTo(200);
        assertThat(update(admin, drive, List.of(), today.plusDays(3)).status()).isEqualTo(200);

        assertThat(jdbc.queryForObject("select count(*) from drive_date_change where drive_id = ?", Integer.class, drive)).isZero();
        assertThat(entries(drive, null)).singleElement().satisfies(e -> assertThat(e.originalDate()).isNull());
    }

    @Test
    void twoSuccessiveMovesResolveToTheFirstOldDate() {
        String admin = admin();
        UUID drive = drive(admin, college(admin), List.of(), today.plusDays(2));
        assertThat(update(admin, drive, List.of(), today.plusDays(5)).status()).isEqualTo(200);
        assertThat(update(admin, drive, List.of(), today.plusDays(8)).status()).isEqualTo(200);

        assertThat(entries(drive, null)).singleElement().satisfies(e -> {
            assertThat(e.date()).isEqualTo(today.plusDays(8));
            assertThat(e.originalDate()).isEqualTo(today.plusDays(2));
        });
    }

    @Test
    void theTriggerRefusesUpdateAndDeleteOfADateChange() {
        String admin = admin();
        UUID drive = drive(admin, college(admin), List.of(), today.plusDays(2));
        assertThat(update(admin, drive, List.of(), today.plusDays(5)).status()).isEqualTo(200);

        assertThatThrownBy(() -> jdbc.update("update drive_date_change set new_date = ? where drive_id = ?", today, drive))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("delete from drive_date_change where drive_id = ?", drive))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void interviewersAreReturnedAsAttendeesAndAUserFilterMatchesSchedulerOrInterviewer() {
        String admin = admin();
        UUID interviewer = signInAs(Role.MANAGER).userId();
        UUID stranger = signInAs(Role.MANAGER).userId();
        UUID drive = drive(admin, college(admin), List.of(interviewer), today.plusDays(2));

        assertThat(entries(drive, null)).singleElement().satisfies(e -> {
            assertThat(e.attendeeUserIds()).containsExactly(interviewer);
            assertThat(e.ownerUserId()).isNotEqualTo(interviewer);
        });
        assertThat(entries(drive, interviewer)).hasSize(1);
        assertThat(entries(drive, stranger)).isEmpty();
    }
}
