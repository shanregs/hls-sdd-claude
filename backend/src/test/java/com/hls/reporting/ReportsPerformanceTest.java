package com.hls.reporting;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import com.hls.support.AttendanceTestBase;
import jakarta.persistence.EntityManagerFactory;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Spec 014a FR-013 / T029: at 500 Teachers and 50 Managers the Attendance report answers its first page in under 3 seconds and
 * its export in under 30, and the number of statements does not grow with the number of people (no query per row).
 */
class ReportsPerformanceTest extends AttendanceTestBase {

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private void seed(String admin, UUID zone, UUID[] schools, int managers, int teachers) {
        for (int i = 0; i < managers; i++) {
            newManager(admin, zone);
        }
        for (int i = 0; i < teachers; i++) {
            UUID teacher = teacher(admin);
            Resp placed = placeTeacherRaw(admin, teacher, schools[i % schools.length], LocalDate.now().minusDays(150));
            assertThat(placed.status()).as(placed.body()).isEqualTo(200);
        }
    }

    private long statementsOf(String url, String token) {
        Statistics stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        stats.setStatisticsEnabled(true);
        get(url, token);
        stats.clear();
        Resp resp = get(url, token);
        assertThat(resp.status()).as(resp.body()).isEqualTo(200);
        return stats.getPrepareStatementCount();
    }

    @Test
    void theAttendanceReportIsFastAtFullSizeAndItsStatementsIgnoreTheVolume() {
        String admin = signInAs(Role.ADMIN).token();
        UUID zone = zone(admin);
        UUID[] schools = new UUID[5];
        for (int i = 0; i < schools.length; i++) {
            schools[i] = school(admin, place(admin, zone));
        }
        YearMonth month = YearMonth.from(today()).minusMonths(1);
        String url = "/api/v1/reports/attendance?month=" + month;

        seed(admin, zone, schools, 5, 20);
        long small = statementsOf(url, admin);

        seed(admin, zone, schools, 45, 480);
        long start = System.nanoTime();
        Resp first = get(url, admin);
        long firstMillis = (System.nanoTime() - start) / 1_000_000;
        assertThat(first.status()).as(first.body()).isEqualTo(200);
        start = System.nanoTime();
        Resp export = get("/api/v1/reports/attendance/export?month=" + month, admin);
        long exportMillis = (System.nanoTime() - start) / 1_000_000;
        assertThat(export.status()).isEqualTo(200);
        long large = statementsOf(url, admin);

        assertThat(small).as("statements counted").isGreaterThan(0);
        assertThat(firstMillis).as("first page in ms").isLessThan(3_000);
        assertThat(exportMillis).as("export in ms").isLessThan(30_000);
        assertThat(large).as("statements at 500 Teachers and 50 Managers vs 20 and 5").isEqualTo(small);
    }
}
