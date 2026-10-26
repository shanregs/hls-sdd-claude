package com.hls.schoolbilling;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Spec 022 SC-008: the overview and ageing of 500 Schools with 12 months of dues load in under 3 seconds. */
class ReceivablesVolumeTest extends ReceivablesTestBase {

    private static final String PREFIX = "VOLUME TEST SCHOOL ";

    @AfterEach
    void cleanUp() {
        jdbc.execute("alter table monthly_due disable trigger trg_monthly_due_write_once");
        try {
            jdbc.update("delete from monthly_due where reference like 'VOL-%'");
        } finally {
            jdbc.execute("alter table monthly_due enable trigger trg_monthly_due_write_once");
        }
        jdbc.update("delete from school where name like ?", PREFIX + "%");
    }

    @Test
    void overviewAndAgeingOf500SchoolsWithATwelveMonthHistoryAreFast() {
        String admin = signInAs(Role.ADMIN).token();
        UUID zone = zone(admin);
        UUID place = place(admin, zone);
        jdbc.update(
                """
                insert into school (id, name, place_id, address, active, version, created_at, updated_at)
                select gen_random_uuid(), ? || g, ?, 'Volume test address', true, 0, now(), now() from generate_series(1, 500) g
                """,
                PREFIX, place);
        jdbc.update(
                """
                insert into monthly_due (id, reference, school_id, month, raised_on, due_on, total, created_at)
                select gen_random_uuid(), 'VOL-' || row_number() over (), s.id,
                       (date_trunc('month', current_date) - (m || ' month')::interval)::date,
                       (date_trunc('month', current_date) - (m || ' month')::interval)::date,
                       (date_trunc('month', current_date) - (m || ' month')::interval)::date + 15,
                       50000, now()
                from school s cross join generate_series(0, 11) m where s.name like ?
                """,
                PREFIX + "%");
        assertThat(jdbc.queryForObject("select count(*) from monthly_due where reference like 'VOL-%'", Integer.class)).isEqualTo(6000);

        Instant start = Instant.now();
        Resp overview = get(BILLING + "/overview?month=" + thisMonth() + "&zoneId=" + zone, admin);
        Duration overviewTime = Duration.between(start, Instant.now());
        start = Instant.now();
        Resp ageing = get(BILLING + "/ageing?zoneId=" + zone, admin);
        Duration ageingTime = Duration.between(start, Instant.now());

        assertThat(overview.status()).isEqualTo(200);
        assertThat(ageing.status()).isEqualTo(200);
        assertThat(overviewTime).isLessThan(Duration.ofSeconds(3));
        assertThat(ageingTime).isLessThan(Duration.ofSeconds(3));
    }
}
