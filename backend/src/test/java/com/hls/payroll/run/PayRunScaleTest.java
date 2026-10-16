package com.hls.payroll.run;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import com.hls.support.PayrollRunTestBase;
import jakarta.persistence.EntityManagerFactory;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

/**
 * Spec 013b T025 (SC-002): a run for 1,000 people is computed in under two minutes while other requests keep being
 * answered, and the connection pool never starves. The people are staff (Directors) with a salary, generated in one SQL
 * statement; the generated rows are removed afterwards because the database is shared with every other test class.
 */
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class PayRunScaleTest extends PayrollRunTestBase {

    private static final String PHONE_PREFIX = "scale";
    private static final int PEOPLE = 1000;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @BeforeEach
    @AfterEach
    void clean() {
        resetRuns();
        // person_salary is append-only by trigger; the generated rows are test data and are removed with the trigger off
        jdbc.execute("alter table person_salary disable trigger user");
        try {
            jdbc.update("delete from person_salary where person_id in (select id from app_user where phone like ?)", PHONE_PREFIX + "%");
        } finally {
            jdbc.execute("alter table person_salary enable trigger user");
        }
        jdbc.update("delete from role_assignment where user_id in (select id from app_user where phone like ?)", PHONE_PREFIX + "%");
        jdbc.update("delete from app_user where phone like ?", PHONE_PREFIX + "%");
    }

    private void generatePeople() {
        jdbc.update("insert into app_user (id, display_name, phone, active)"
                + " select gen_random_uuid(), 'Scale Person ' || g, ? || g, true from generate_series(1, ?) g",
                PHONE_PREFIX,
                PEOPLE);
        jdbc.update("insert into role_assignment (id, user_id, role)"
                + " select gen_random_uuid(), id, 'DIRECTOR' from app_user where phone like ?",
                PHONE_PREFIX + "%");
        jdbc.update("insert into person_salary (id, person_kind, person_id, amount, effective_on, note, recorded_by, recorded_at)"
                + " select gen_random_uuid(), 'STAFF', id, 30000, ?, null, gen_random_uuid(), now()"
                + " from app_user where phone like ?",
                LocalDate.now().minusDays(400),
                PHONE_PREFIX + "%");
    }

    @Test
    void aThousandPeopleAreComputedInUnderTwoMinutesWhileOtherRequestsAnswer() throws Exception {
        generatePeople();
        String admin = signInAs(Role.ADMIN).token();
        String director = signInAs(Role.DIRECTOR).token();
        long began = System.nanoTime();
        UUID run = startRun(admin).id();

        // other requests keep answering while the batch runs; a starved pool would answer 500 after the 2 s timeout
        List<Long> latencies = new ArrayList<>();
        Map<String, Object> state;
        do {
            long t = System.nanoTime();
            Resp other = get(RUNS + "?size=5", director);
            latencies.add(Duration.ofNanos(System.nanoTime() - t).toMillis());
            assertThat(other.status()).as("a request during the run: " + other.body()).isEqualTo(200);
            state = get(RUNS + "/" + run, admin).map();
            Thread.sleep(100);
        } while (!"DONE".equals(state.get("computeState")) && !"FAILED".equals(state.get("computeState")));

        Duration took = Duration.ofNanos(System.nanoTime() - began);
        assertThat(state.get("computeState")).as(state.toString()).isEqualTo("DONE");
        assertThat(took).as("the run took " + took).isLessThan(Duration.ofMinutes(2));
        assertThat(((Number) state.get("payableCount")).intValue()).isGreaterThanOrEqualTo(PEOPLE);
        assertThat(latencies).as("an unrelated request stays quick during the run").allSatisfy(ms -> assertThat(ms).isLessThan(2000L));
        assertThat(allLines(admin, run).stream().filter(l -> ((String) l.get("name")).startsWith("Scale Person")).count())
                .isEqualTo(PEOPLE);
    }

    @Test
    void theLinesEndpointUsesAFixedNumberOfQueriesWhateverThePageSize() {
        generatePeople();
        String admin = signInAs(Role.ADMIN).token();
        UUID run = startAndCompute(admin);
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        get(RUNS + "/" + run + "/lines?size=5", admin); // warm up the permission lookup

        statistics.clear();
        Resp small = get(RUNS + "/" + run + "/lines?size=5", admin);
        long smallQueries = statistics.getPrepareStatementCount();
        statistics.clear();
        Resp large = get(RUNS + "/" + run + "/lines?size=200", admin);
        long largeQueries = statistics.getPrepareStatementCount();

        assertThat(small.status()).isEqualTo(200);
        assertThat(large.status()).isEqualTo(200);
        assertThat(content(large)).hasSize(200);
        assertThat(largeQueries).as("queries for 200 lines vs 5 lines").isEqualTo(smallQueries);
        assertThat(largeQueries).isLessThanOrEqualTo(6L);
    }
}
