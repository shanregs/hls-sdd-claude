package com.hls.attendance.manager;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import jakarta.persistence.EntityManagerFactory;
import java.time.LocalDate;
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

/** Spec 032 US5: the Admin and Director grid of every Manager's month, in a fixed number of queries. */
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class ManagerGridTest extends ManagerAttendanceTestBase {

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private Resp grid(String token, YearMonth month, String extra) {
        return get(ATT + "/grid?month=" + month + extra, token);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> rowOf(Resp grid, UUID managerId) {
        return ((List<Map<String, Object>>) grid.map().get("managers"))
                .stream().filter(r -> managerId.toString().equals(r.get("managerId"))).findFirst().orElseThrow();
    }

    @Test
    void theGridListsEveryManagerWithAStatusPerDayAndTheMonthFigures() {
        String admin = signInAs(Role.ADMIN).token();
        String director = signInAs(Role.DIRECTOR).token();
        ManagerCtx a = longJoinedManager(admin);
        ManagerCtx b = longJoinedManager(admin);
        YearMonth month = lastMonth();
        List<LocalDate> wd = workingDates(month);
        seedDay(a.managerId(), wd.get(0), instant(wd.get(0), 9, 0), instant(wd.get(0), 18, 0));
        seedFieldDay(a.managerId(), wd.get(1), "Visit", instant(wd.get(1), 9, 0), instant(wd.get(1), 18, 0));
        seedLeave(b.managerId(), wd.get(0), 1.0);

        for (String token : new String[] {admin, director}) {
            Resp resp = grid(token, month, "");
            assertThat(resp.status()).as(resp.body()).isEqualTo(200);
            assertThat(resp.map()).containsEntry("month", month.toString());
            Map<String, Object> rowA = rowOf(resp, a.managerId());
            Map<String, Object> rowB = rowOf(resp, b.managerId());
            assertThat((List<?>) rowA.get("statuses")).hasSize(month.lengthOfMonth());
            assertThat(((List<?>) rowA.get("statuses")).get(wd.get(0).getDayOfMonth() - 1)).isEqualTo("WORKED");
            assertThat(((List<?>) rowA.get("statuses")).get(wd.get(1).getDayOfMonth() - 1)).isEqualTo("FIELD");
            assertThat(((List<?>) rowB.get("statuses")).get(wd.get(0).getDayOfMonth() - 1)).isEqualTo("LEAVE");
            @SuppressWarnings("unchecked")
            Map<String, Object> figures = (Map<String, Object>) rowA.get("figures");
            assertThat(((Number) figures.get("daysWorked")).doubleValue()).isEqualTo(2.0);
            assertThat(((Number) figures.get("fieldDays")).doubleValue()).isEqualTo(1.0);
            assertThat(rowA).containsEntry("locked", false);
        }
    }

    @Test
    void theZoneFilterKeepsTheManagersWhoHoldThatZoneAndSearchMatchesTheName() {
        String admin = signInAs(Role.ADMIN).token();
        UUID[] zone = schoolInNewZone(admin);
        ManagerCtx inZone = newManager(admin, zone[0]);
        ManagerCtx elsewhere = longJoinedManager(admin);
        setJoining(inZone.managerId(), today().minusMonths(6), null);

        Resp filtered = grid(admin, lastMonth(), "&zoneId=" + zone[0]);

        assertThat(filtered.status()).as(filtered.body()).isEqualTo(200);
        List<?> rows = (List<?>) filtered.map().get("managers");
        assertThat(rows).hasSize(1);
        assertThat(rowOf(filtered, inZone.managerId())).isNotNull();

        String name = (String) rowOf(grid(admin, lastMonth(), ""), elsewhere.managerId()).get("name");
        Resp searched = grid(admin, lastMonth(), "&q=" + name.split(" ")[0].toUpperCase());
        assertThat(searched.status()).as(searched.body()).isEqualTo(200);
        assertThat(rowOf(searched, elsewhere.managerId())).isNotNull();
        assertThat(((List<?>) grid(admin, lastMonth(), "&q=zzzz-nobody-zzzz").map().get("managers"))).isEmpty();
    }

    @Test
    void theGridCarriesNoLocationAndNoPhoto() {
        String admin = signInAs(Role.ADMIN).token();
        ManagerCtx manager = longJoinedManager(admin);
        YearMonth month = lastMonth();
        LocalDate day = workingDates(month).get(0);
        UUID id = seedDay(manager.managerId(), day, instant(day, 9, 0), instant(day, 18, 0));
        jdbc.update("update manager_day set photo_file_id = ? where id = ?", UUID.randomUUID(), id);

        String body = grid(admin, month, "").body();

        assertThat(body).doesNotContain("latitude").doesNotContain("longitude").doesNotContain("photo").doesNotContain("Place");
    }

    @Test
    void aGridOfFortyManagersIsBuiltInAFixedNumberOfQueries() {
        String admin = signInAs(Role.ADMIN).token();
        YearMonth month = lastMonth();
        LocalDate day = workingDates(month).get(0);
        List<ManagerCtx> managers = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            ManagerCtx m = longJoinedManager(admin);
            seedDay(m.managerId(), day, instant(day, 9, 0), instant(day, 18, 0));
            managers.add(m);
        }
        Statistics stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();

        long lowest = Long.MAX_VALUE;
        for (int run = 0; run < 3; run++) {
            sleep(400);
            stats.clear();
            assertThat(grid(admin, month, "").status()).isEqualTo(200);
            lowest = Math.min(lowest, stats.getPrepareStatementCount());
        }

        assertThat(lowest).as("statements for a grid of 40+ Managers").isLessThanOrEqualTo(12);
        assertThat(managers).hasSize(40);
    }

    @Test
    void aManagerAndATeacherGet403() {
        String admin = signInAs(Role.ADMIN).token();
        ManagerCtx manager = longJoinedManager(admin);

        assertThat(grid(manager.token(), lastMonth(), "").status()).isEqualTo(403);
        assertThat(grid(teacher().token(), lastMonth(), "").status()).isEqualTo(403);
        assertThat(grid(admin, lastMonth(), "").status()).isEqualTo(200);
        assertThat(get(ATT + "/grid", admin).status()).isEqualTo(400);
        assertThat(get(ATT + "/grid?month=garbage", admin).status()).isEqualTo(400);
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
