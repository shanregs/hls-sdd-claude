package com.hls.training.calendar;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.training.calendar.internal.ReminderJob;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Spec 024 T059: reminders at the configured offsets, once per person and offset. */
class ReminderJobTest extends TrainingCalendarTestBase {

    @Autowired
    ReminderJob job;

    @AfterEach
    void restoreSettings() {
        jdbc.update("delete from system_setting where updated_by is not null");
    }

    private int reminders(UUID user) {
        return notifications(user, "TRAINING_REMINDER");
    }

    private void moveTo(World w, UUID session, java.time.LocalDate to) {
        Map<String, Object> view = session(w.admin(), session).map();
        Map<String, Object> body = new HashMap<>();
        body.put("type", "MONTHLY");
        body.put("topic", view.get("topic"));
        body.put("date", to.toString());
        body.put("startsAt", view.get("startsAt"));
        body.put("endsAt", view.get("endsAt"));
        body.put("venueKind", "PHYSICAL");
        body.put("venue", "Head office");
        body.put("reason", "Venue changed");
        body.put("version", view.get("version"));
        assertThat(put(API + "/sessions/" + session, w.admin(), body).status()).isEqualTo(200);
    }

    @Test
    void remindsOncePerPersonAndOffsetAndASecondRunSendsNothing() {
        World w = newWorld();
        UUID user = w.teacherA().signed().userId();
        LocalDateHolder day = new LocalDateHolder(today().plusDays(2));
        UUID session = publishedSession(w.admin(), "MONTHLY", day.date, null);
        assignRow(session, "TEACHER", w.teacherA().teacherId());

        job.run(today());
        assertThat(reminders(user)).isEqualTo(1);
        job.run(today());
        assertThat(reminders(user)).isEqualTo(1);

        // one day before: the second offset
        job.run(today().plusDays(1));
        assertThat(reminders(user)).isEqualTo(2);
        job.run(today().plusDays(1));
        assertThat(reminders(user)).isEqualTo(2);
    }

    @Test
    void aSecondOffsetOfZeroTurnsTheSecondReminderOff() {
        World w = newWorld();
        assertThat(put("/api/v1/settings/system", w.admin(), Map.of("values", Map.of("training.reminder-days-second", "0"))).status())
                .isEqualTo(200);
        UUID user = w.teacherA().signed().userId();
        UUID session = publishedSession(w.admin(), "MONTHLY", today().plusDays(2), null);
        assignRow(session, "TEACHER", w.teacherA().teacherId());
        job.run(today());
        job.run(today().plusDays(1));
        assertThat(reminders(user)).isEqualTo(1);
    }

    @Test
    void registeredTeachersAreRemindedToo() {
        World w = newWorld();
        UUID session = publishedSession(w.admin(), "WEEKEND", today().plusDays(2), 5);
        assertThat(register(w.teacherB().token(), session).status()).isEqualTo(201);
        job.run(today());
        assertThat(reminders(w.teacherB().signed().userId())).isEqualTo(1);
        assertThat(reminders(w.teacherA().signed().userId())).isZero();
    }

    @Test
    void aMovedSessionClearsTheKeysAndSendsAChangeNotice() {
        World w = newWorld();
        UUID user = w.teacherA().signed().userId();
        UUID session = publishedSession(w.admin(), "MONTHLY", today().plusDays(2), null);
        assignRow(session, "TEACHER", w.teacherA().teacherId());
        job.run(today());
        assertThat(reminders(user)).isEqualTo(1);

        moveTo(w, session, today().plusDays(3));
        assertThat(notifications(user, "TRAINING_CHANGED")).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from training_reminder_sent where session_id = ?", Integer.class, session)).isZero();

        // the new date is two days away tomorrow, so the person is reminded again
        job.run(today().plusDays(1));
        assertThat(reminders(user)).isEqualTo(2);
    }

    @Test
    void aCancelledSessionSendsAChangeNoticeAndNoReminder() {
        World w = newWorld();
        UUID user = w.teacherB().signed().userId();
        UUID session = publishedSession(w.admin(), "MONTHLY", today().plusDays(2), null);
        assignRow(session, "TEACHER", w.teacherB().teacherId());
        Resp cancelled = post(API + "/sessions/" + session + "/cancel", w.admin(), Map.of("reason", "Trainer unwell", "version", versionOf(w.admin(), session)));
        assertThat(cancelled.status()).as(cancelled.body()).isEqualTo(200);
        assertThat(notifications(user, "TRAINING_CHANGED")).isEqualTo(1);
        job.run(today());
        assertThat(reminders(user)).isZero();
    }

    private record LocalDateHolder(java.time.LocalDate date) {}
}
