package com.hls.training.calendar;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

/** Spec 024 T016: registration under a seat limit, the start-time cut-off and who may register. */
class RegistrationTest extends TrainingCalendarTestBase {

    @Test
    void theSeatLimitHoldsAndACancelledSeatCanBeTaken() {
        World w = newWorld();
        TeacherCtx t1 = newTeacher(w.admin(), w.schoolA(), 60);
        TeacherCtx t2 = newTeacher(w.admin(), w.schoolA(), 60);
        TeacherCtx t3 = newTeacher(w.admin(), w.schoolB(), 60);
        TeacherCtx t4 = newTeacher(w.admin(), w.schoolB(), 60);
        UUID session = publishedSession(w.admin(), "WEEKEND", in(10), 3);

        assertThat(register(t1.token(), session).status()).isEqualTo(201);
        assertThat(register(t2.token(), session).status()).isEqualTo(201);
        assertThat(register(t3.token(), session).status()).isEqualTo(201);
        Resp full = register(t4.token(), session);
        assertThat(full.status()).isEqualTo(409);
        assertThat(full.body()).contains("no seats left");

        // registering again is not an error and takes no second seat
        assertThat(register(t1.token(), session).status()).isEqualTo(200);
        assertThat(registeredCount(session)).isEqualTo(3);

        assertThat(cancelRegistration(t3.token(), session).status()).isEqualTo(200);
        assertThat(registeredCount(session)).isEqualTo(2);
        assertThat(register(t4.token(), session).status()).isEqualTo(201);
        assertThat(registeredCount(session)).isEqualTo(3);

        // the same Teacher can come back to a cancelled seat while one is free: one row flips, it is not duplicated
        assertThat(cancelRegistration(t4.token(), session).status()).isEqualTo(200);
        assertThat(register(t3.token(), session).status()).isEqualTo(201);
        assertThat(jdbc.queryForObject("select count(*) from training_registration where session_id = ?", Integer.class, session)).isEqualTo(4);
    }

    @Test
    void cancellingAfterTheStartIsRefused() {
        World w = newWorld();
        TeacherCtx t1 = newTeacher(w.admin(), w.schoolA(), 60);
        UUID session = publishedSession(w.admin(), "WEEKEND", in(3), 5);
        assertThat(register(t1.token(), session).status()).isEqualTo(201);

        startSessionAlready(session);

        Resp late = cancelRegistration(t1.token(), session);
        assertThat(late.status()).isEqualTo(409);
        assertThat(registeredCount(session)).isEqualTo(1);
        // and nobody can register once the session has started
        TeacherCtx t2 = newTeacher(w.admin(), w.schoolA(), 60);
        assertThat(register(t2.token(), session).status()).isEqualTo(409);
    }

    @Test
    void onlyActiveTeachersRegister() {
        World w = newWorld();
        UUID session = publishedSession(w.admin(), "WEEKEND", in(10), null);
        for (String status : List.of("IN_TRAINING", "EXITED", "ON_LEAVE")) {
            TeacherCtx teacher = newTeacher(w.admin(), w.schoolA(), 60);
            setTeacherStatus(teacher.teacherId(), status);
            Resp refused = register(teacher.token(), session);
            assertThat(refused.status()).as(status).isEqualTo(409);
        }
        assertThat(registeredCount(session)).isZero();
    }

    @Test
    void onlyOpenWeekendSessionsTakeRegistrations() {
        World w = newWorld();
        TeacherCtx t1 = newTeacher(w.admin(), w.schoolA(), 60);
        UUID monthly = publishedSession(w.admin(), "MONTHLY", in(10), null);
        UUID draft = draftSession(w.admin(), "WEEKEND", in(10));

        assertThat(register(t1.token(), monthly).status()).isEqualTo(409);
        // a draft is not shown to a Teacher at all
        assertThat(register(t1.token(), draft).status()).isEqualTo(404);
        assertThat(registerOnBehalf(w.admin(), draft, t1.teacherId()).status()).isEqualTo(409);
    }

    @Test
    void adminAndDirectorRegisterOnBehalfAndManagersCannot() {
        World w = newWorld();
        TeacherCtx t1 = newTeacher(w.admin(), w.schoolA(), 60);
        UUID session = publishedSession(w.admin(), "WEEKEND", in(10), 2);

        assertThat(registerOnBehalf(w.admin(), session, t1.teacherId()).status()).isEqualTo(201);
        assertThat(registeredCount(session)).isEqualTo(1);
        assertThat(notifications(t1.signed().userId(), "TRAINING_REGISTERED")).isEqualTo(1);
        assertThat(registerOnBehalf(w.managerA().token(), session, t1.teacherId()).status()).isEqualTo(403);
        assertThat(register(w.managerA().token(), session).status()).isEqualTo(403);
        assertThat(register(signInAs(Role.SYSTEM).token(), session).status()).isEqualTo(403);
        assertThat(registerOnBehalf(w.director(), session, newTeacher(w.admin(), w.schoolA(), 60).teacherId()).status()).isEqualTo(201);
        // the roster is for staff
        assertThat(get(API + "/sessions/" + session + "/registrations", w.admin()).status()).isEqualTo(200);
        assertThat(get(API + "/sessions/" + session + "/registrations", t1.token()).status()).isEqualTo(403);
    }

    @Test
    void tenParallelRegistrationsForThreeSeatsEndWithExactlyThree() throws Exception {
        World w = newWorld();
        List<TeacherCtx> teachers = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            teachers.add(newTeacher(w.admin(), w.schoolA(), 60));
        }
        UUID session = publishedSession(w.admin(), "WEEKEND", in(10), 3);

        ExecutorService pool = Executors.newFixedThreadPool(10);
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (TeacherCtx teacher : teachers) {
                Callable<Integer> task = () -> register(teacher.token(), session).status();
                results.add(pool.submit(task));
            }
            int created = 0;
            int refused = 0;
            for (Future<Integer> result : results) {
                int status = result.get();
                if (status == 201) {
                    created++;
                } else if (status == 409) {
                    refused++;
                }
            }
            assertThat(created).isEqualTo(3);
            assertThat(refused).isEqualTo(7);
        } finally {
            pool.shutdownNow();
        }
        assertThat(registeredCount(session)).isEqualTo(3);
    }
}
