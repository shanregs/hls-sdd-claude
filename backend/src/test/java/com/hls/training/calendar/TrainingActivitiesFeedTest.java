package com.hls.training.calendar;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.training.api.TrainingActivities;
import com.hls.training.api.TrainingActivities.ActivityEntry;
import jakarta.persistence.EntityManagerFactory;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

/** Spec 024 T057: the C1 feed of training sessions (contracts/planned-activity-feed.md section 1). */
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class TrainingActivitiesFeedTest extends TrainingCalendarTestBase {

    @Autowired
    TrainingActivities feed;

    @Autowired
    EntityManagerFactory entityManagerFactory;

    private ActivityEntry entry(UUID session, LocalDate day) {
        return feed.activitiesBetween(day, day, null).stream().filter(e -> e.id().equals(session)).findFirst().orElse(null);
    }

    private LocalDate freshDay() {
        // a day far from other tests' sessions so the range holds only this test's rows
        return LocalDate.of(2031, 1, 1).plusDays(Math.abs(UUID.randomUUID().hashCode()) % 3000);
    }

    private UUID sessionOn(String token, LocalDate day, String type) {
        return publishedSession(token, type, day, "WEEKEND".equals(type) ? 10 : null);
    }

    @Test
    void oneEntryPerPublishedSessionWithOwnerAndManagerAttendeesOnly() {
        World w = newWorld();
        LocalDate day = freshDay();
        UUID session = sessionOn(w.admin(), day, "MONTHLY");
        assignRow(session, "MANAGER", w.managerA().managerId());
        assignRow(session, "TEACHER", w.teacherA().teacherId());

        ActivityEntry e = entry(session, day);
        assertThat(e).isNotNull();
        assertThat(e.kind()).isEqualTo("TRAINING");
        assertThat(e.subKind()).isEqualTo("MONTHLY");
        assertThat(e.status()).isEqualTo("PLANNED");
        assertThat(e.attendeeUserIds()).containsExactly(w.managerA().signed().userId());
        assertThat(e.attendeeUserIds()).doesNotContain(w.teacherA().signed().userId());
        assertThat(e.completedOn()).isNull();
        assertThat(e.zoneId()).isNull();
        assertThat(e.schoolId()).isNull();
        assertThat(feed.activitiesBetween(day, day, null).stream().filter(x -> x.id().equals(session))).hasSize(1);
    }

    @Test
    void draftAndSkippedAreNeverReturnedAndStatusesFollowTheSession() {
        World w = newWorld();
        LocalDate day = freshDay();
        UUID draft = draftSession(w.admin(), "MONTHLY", day);
        UUID skipped = sessionOn(w.admin(), day, "MONTHLY");
        UUID pattern = createPattern(w.admin(), patternBody(LocalDate.of(2040, 1, 1)));
        jdbc.update(
                "update training_session set status = 'SKIPPED', skip_reason = 'x', pattern_id = ?, pattern_month = ? where id = ?",
                pattern, day.withDayOfMonth(1), skipped);
        UUID held = sessionOn(w.admin(), day, "MONTHLY");
        jdbc.update("update training_session set status = 'HELD' where id = ?", held);
        UUID cancelled = sessionOn(w.admin(), day, "MONTHLY");
        jdbc.update("update training_session set status = 'CANCELLED' where id = ?", cancelled);

        assertThat(entry(draft, day)).isNull();
        assertThat(entry(skipped, day)).isNull();
        assertThat(entry(held, day).status()).isEqualTo("HELD");
        assertThat(entry(held, day).completedOn()).isEqualTo(day);
        assertThat(entry(cancelled, day).status()).isEqualTo("CANCELLED");
        assertThat(entry(cancelled, day).completedOn()).isNull();
        // plannedBetween is published sessions only
        List<UUID> planned = feed.plannedBetween(day, day, null).stream().map(TrainingActivities.PlannedActivity::id).toList();
        assertThat(planned).doesNotContain(draft, skipped, held, cancelled);
    }

    @Test
    void theOriginalDateSurvivesOneAndTwoMoves() {
        World w = newWorld();
        LocalDate first = freshDay();
        UUID session = sessionOn(w.admin(), first, "MONTHLY");
        assertThat(entry(session, first).originalDate()).isNull();
        move(w, session, first.plusDays(3), "Venue clash");
        assertThat(entry(session, first.plusDays(3)).originalDate()).isEqualTo(first);
        move(w, session, first.plusDays(6), "Trainer away");
        assertThat(entry(session, first.plusDays(6)).originalDate()).isEqualTo(first);
    }

    private void move(World w, UUID session, LocalDate to, String reason) {
        Map<String, Object> view = session(w.admin(), session).map();
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("type", "MONTHLY");
        body.put("topic", view.get("topic"));
        body.put("date", to.toString());
        body.put("startsAt", view.get("startsAt"));
        body.put("endsAt", view.get("endsAt"));
        body.put("venueKind", "PHYSICAL");
        body.put("venue", "Head office");
        body.put("reason", reason);
        body.put("version", view.get("version"));
        assertThat(put(API + "/sessions/" + session, w.admin(), body).status()).isEqualTo(200);
    }

    @Test
    void aUserFilterMatchesOwnerOrAttendeeAndNothingPrivateIsExposed() {
        World w = newWorld();
        LocalDate day = freshDay();
        UUID session = sessionOn(w.admin(), day, "WEEKEND");
        assignRow(session, "MANAGER", w.managerA().managerId());
        UUID managerUser = w.managerA().signed().userId();
        UUID otherUser = w.managerB().signed().userId();

        assertThat(feed.activitiesBetween(day, day, managerUser).stream().map(ActivityEntry::id)).contains(session);
        assertThat(feed.activitiesBetween(day, day, otherUser).stream().map(ActivityEntry::id)).doesNotContain(session);
        UUID owner = jdbc.queryForObject("select created_by from training_session where id = ?", UUID.class, session);
        assertThat(feed.activitiesBetween(day, day, owner).stream().map(ActivityEntry::id)).contains(session);
        assertThat(feed.plannedBetween(day, day, managerUser).stream().map(TrainingActivities.PlannedActivity::id)).contains(session);
        // a venue is a place; a virtual link is only ever "Online"
        String record = entry(session, day).toString();
        assertThat(record).doesNotContain("Phonics").doesNotContain("Trainer T");
    }

    @Test
    void aFixedNumberOfQueriesForTwoHundredSessions() {
        World w = newWorld();
        LocalDate day = freshDay();
        for (int i = 0; i < 200; i++) {
            UUID s = sessionOn(w.admin(), day, "MONTHLY");
            if (i % 10 == 0) {
                assignRow(s, "MANAGER", w.managerA().managerId());
            }
        }
        Statistics stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        stats.clear();
        List<ActivityEntry> found = feed.activitiesBetween(day, day, null);
        assertThat(found.size()).isGreaterThanOrEqualTo(200);
        assertThat(stats.getPrepareStatementCount()).isLessThanOrEqualTo(8);
    }
}
