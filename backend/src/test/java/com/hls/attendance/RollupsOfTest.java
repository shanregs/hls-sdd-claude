package com.hls.attendance;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.attendance.api.AttendanceReadApi;
import com.hls.attendance.api.RollupView;
import com.hls.support.AttendanceTestBase;
import jakarta.persistence.EntityManagerFactory;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

/** Spec 014a T012a: the bulk {@code rollupsOf} equals {@code rollupOf} for each Teacher and uses a fixed number of queries. */
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class RollupsOfTest extends AttendanceTestBase {

    @Autowired
    private AttendanceReadApi attendance;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private void mark(String token, UUID teacher, YearMonth month, int day, String code, double value) {
        Resp resp = put(
                "/api/v1/attendance/teachers/" + teacher + "/marks/" + month.atDay(day),
                token,
                Map.of("statusCode", code, "dayValue", value));
        assertThat(resp.status()).as(resp.body()).isEqualTo(200);
    }

    private void lock(UUID teacher, YearMonth month) {
        jdbc.update(
                "insert into attendance_teacher_month (id, teacher_id, year_month, state, working_days, days_worked,"
                        + " days_leave, training_available, training_attended, unmarked, weighted_total, changed_at, version)"
                        + " values (?, ?, ?, 'LOCKED', 20, 7, 1, 0, 0, 0, 7, now(), 0)",
                UUID.randomUUID(),
                teacher,
                month.toString());
    }

    @Test
    void bulkRollupsEqualThePerTeacherOnesOverThreeTeachersAndTwoMonths() {
        World w = newWorld();
        UUID t1 = newTeacher(w.admin(), w.schoolA(), 150).teacherId();
        UUID t2 = newTeacher(w.admin(), w.schoolB(), 150).teacherId();
        UUID t3 = newTeacher(w.admin(), w.schoolA(), 150).teacherId();
        YearMonth last = YearMonth.from(today()).minusMonths(1);
        YearMonth before = last.minusMonths(1);
        for (YearMonth m : List.of(last, before)) {
            mark(w.managerA().token(), t1, m, 10, "P", 1);
            mark(w.managerA().token(), t1, m, 11, "L", 1);
            mark(w.managerB().token(), t2, m, 10, "P", 0.5);
        }
        lock(t3, last);
        List<UUID> ids = List.of(t1, t2, t3, UUID.randomUUID());

        for (YearMonth m : List.of(last, before)) {
            Map<UUID, RollupView> bulk = attendance.rollupsOf(ids, m);
            assertThat(bulk).containsOnlyKeys(ids);
            for (UUID id : ids) {
                assertThat(bulk.get(id)).as(id + " " + m).isEqualTo(attendance.rollupOf(id, m));
            }
        }
        assertThat(attendance.rollupsOf(List.of(), last)).isEmpty();
    }

    @Test
    void theNumberOfStatementsDoesNotGrowWithTheTeachersAsked() {
        World w = newWorld();
        List<UUID> few = new ArrayList<>(List.of(w.teacherA().teacherId(), w.teacherB().teacherId()));
        List<UUID> many = new ArrayList<>(few);
        for (int i = 0; i < 8; i++) {
            many.add(newTeacher(w.admin(), w.schoolA(), 60).teacherId());
        }
        YearMonth month = YearMonth.from(today()).minusMonths(1);
        Statistics stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();

        long fewCount = lowest(stats, few, month);
        long manyCount = lowest(stats, many, month);

        assertThat(manyCount).as("statements for 10 asked vs 2").isEqualTo(fewCount);
    }

    private long lowest(Statistics stats, List<UUID> ids, YearMonth month) {
        long lowest = Long.MAX_VALUE;
        for (int run = 0; run < 3; run++) {
            stats.clear();
            attendance.rollupsOf(ids, month);
            lowest = Math.min(lowest, stats.getPrepareStatementCount());
        }
        return lowest;
    }
}
