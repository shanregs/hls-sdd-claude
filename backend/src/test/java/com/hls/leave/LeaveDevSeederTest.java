package com.hls.leave;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.leave.internal.LeaveDevSeeder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

/** Spec 009 FR-014: the demo flag seeds sample leave requests, idempotently, and Tara can sign in with a password. */
@TestPropertySource(properties = "hls.seed.demo-data=true")
class LeaveDevSeederTest extends LeaveTestBase {

    @Autowired
    private LeaveDevSeeder seeder;

    private int requests() {
        return jdbc.queryForObject("select count(*) from leave_request", Integer.class);
    }

    @Test
    void demoLeaveRequestsAreSeededForTheManagerAndTheTeacherWithoutDuplicates() {
        Signed manoj = signIn(null, "9800000003", "Password123!");
        String asManager = get("/api/v1/leave", manoj.token()).body();
        // Tara, Meena and Karthik report to Manoj; Lakshmi's School has no Manager.
        assertThat(asManager).contains("\"pendingCount\":3").contains("Meena Selvi").contains("Karthik Raja");

        Signed asha = signIn(null, "9800000001", "Password123!");
        assertThat(get("/api/v1/leave?size=100", asha.token()).body()).contains("Lakshmi Priya");
        assertThat(asManager).doesNotContain("Lakshmi Priya");

        int before = requests();
        seeder.run(null);
        assertThat(requests()).isEqualTo(before);
    }

    @Test
    void taraSignsInWithAPasswordAndSeesPendingApprovedAndRejectedRequests() {
        Signed tara = signIn(null, "9800000004", "Password123!");

        String mine = get("/api/v1/me/leave?size=100", tara.token()).body();

        assertThat(mine).contains("\"status\":\"PENDING\"").contains("\"status\":\"APPROVED\"").contains("\"status\":\"REJECTED\"");
        assertThat(mine).contains("Annual exams are on that day");
        assertThat(jdbc.queryForObject(
                        "select count(*) from attendance_mark m join leave_request r on r.id = m.leave_request_id"
                                + " where r.status = 'APPROVED'",
                        Integer.class))
                .isPositive();
    }
}
