package com.hls.payroll;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import com.hls.payroll.api.PayRules;
import com.hls.payroll.api.PayRules.Kind;
import com.hls.payroll.api.PayRules.PersonRef;
import com.hls.payroll.api.SalaryInEffect;
import com.hls.payroll.internal.SalaryStructureService;
import com.hls.support.PayrollTestBase;
import jakarta.persistence.EntityManagerFactory;
import java.math.BigDecimal;
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
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Spec 013a (T031): the bulk forms run in a fixed number of queries however many people are asked about, and 300
 * people's salaries come back in under two seconds. The count is Hibernate's prepared-statement count.
 */
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class PayRulesBulkTest extends PayrollTestBase {

    private static final int PEOPLE = 300;

    @Autowired
    private PayRules payRules;

    @Autowired
    private SalaryStructureService structures;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private Statistics statistics() {
        return entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    }

    /**
     * Prepared statements one call needs: the lowest of three runs. Audit entries from earlier requests are written
     * after the request returns and would add to a single run's count; they can only add, never remove.
     */
    private long statementsFor(Runnable call) {
        Statistics stats = statistics();
        long lowest = Long.MAX_VALUE;
        for (int run = 0; run < 3; run++) {
            sleep(400);
            stats.clear();
            call.run();
            lowest = Math.min(lowest, stats.getPrepareStatementCount());
        }
        return lowest;
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** A fresh HTTP client: one test makes about nine hundred requests, more than a single client keeps open. */
    private void freshClient() {
        client = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    private List<PersonRef> threeHundredPlacedTeachersWithSalaries(String admin) {
        UUID[] ids = schoolInNewZone(admin);
        List<PersonRef> people = new ArrayList<>();
        for (int i = 0; i < PEOPLE; i++) {
            if (i % 50 == 0) {
                freshClient();
            }
            UUID teacher = teacher(admin);
            assertThat(placeTeacherRaw(admin, teacher, ids[2], LocalDate.now().minusDays(200)).status()).isEqualTo(200);
            assertThat(post(
                                    "/api/v1/teachers/" + teacher + "/salary",
                                    admin,
                                    Map.of("amount", new BigDecimal("20000").add(BigDecimal.valueOf(i)), "effectiveOn",
                                            LocalDate.now().minusDays(300).toString()))
                            .status())
                    .isEqualTo(201);
            people.add(new PersonRef(Kind.TEACHER, teacher));
        }
        return people;
    }

    @Test
    void theBulkFormsUseAFixedNumberOfQueriesAndThreeHundredSalariesComeBackFast() {
        String admin = signInAs(Role.ADMIN).token();
        List<PersonRef> people = threeHundredPlacedTeachersWithSalaries(admin);
        List<PersonRef> few = people.subList(0, 30);
        LocalDate date = LocalDate.now().minusMonths(1);
        YearMonth month = YearMonth.from(date);

        // warm up caches and the policy lookups so only the per-call queries are counted
        payRules.salariesOn(few, date);
        payRules.payableDays(few, month);

        long salaries30 = statementsFor(() -> payRules.salariesOn(few, date));
        long salaries300 = statementsFor(() -> payRules.salariesOn(people, date));
        assertThat(salaries300).as("salariesOn statements for 300 vs 30 people").isEqualTo(salaries30);

        long payable30 = statementsFor(() -> payRules.payableDays(few, month));
        long payable300 = statementsFor(() -> payRules.payableDays(people, month));
        assertThat(payable300).as("payableDays statements for 300 vs 30 people").isEqualTo(payable30);
        assertThat(payable300).isLessThanOrEqualTo(10);

        long start = System.nanoTime();
        Map<PersonRef, SalaryInEffect> salaries = payRules.salariesOn(people, date);
        long millis = (System.nanoTime() - start) / 1_000_000;
        assertThat(salaries).hasSize(PEOPLE).allSatisfy((p, s) -> assertThat(s).isInstanceOf(SalaryInEffect.Found.class));
        assertThat(millis).as("salariesOn for 300 people, ms").isLessThan(2000);
    }

    @Test
    void theDesignationListUsesAFixedNumberOfQueriesHoweverManyDesignationsThereAre() {
        String admin = signInAs(Role.ADMIN).token();
        // a Teacher holder and a priceless Manager designation, so every query of the report runs in the baseline
        teacherEmployment(admin, teacher(admin), designation(admin, "TEACHER"), null);
        designation(admin, "MANAGER");
        structures.list(null, true); // warm up
        long few = statementsFor(() -> structures.list(null, true));
        for (int i = 0; i < 20; i++) {
            UUID teachers = designation(admin, "TEACHER");
            UUID managers = designation(admin, "MANAGER");
            recordSalaryOk(admin, managers, "10000", firstOfMonth());
            teacherEmployment(admin, teacher(admin), teachers, null);
        }
        long many = statementsFor(() -> structures.list(null, true));
        assertThat(many).as("list statements with more designations").isEqualTo(few);
    }
}
