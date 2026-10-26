package com.hls;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/** The dev-only demo data (hls.seed.demo-data=true) builds a consistent Zone/School/Manager/Teacher set. */
@TestPropertySource(properties = "hls.seed.demo-data=true")
class DevSeedTest extends IntegrationTestBase {

    @Test
    void demoDataIsSeededConsistentlyAndSignInStillWorks() {
        Signed admin = signIn(null, "9800000001", "Password123!");

        assertThat(get("/api/v1/zones?query=Demo Zone", admin.token()).body())
                .contains("Demo Zone")
                .contains("\"placeCount\":2")
                .contains("\"schoolCount\":2")
                .contains("\"managerCount\":1");
        assertThat(get("/api/v1/managers", admin.token()).body()).contains("Manoj Manager");
        String teachers = get("/api/v1/teachers?query=Tara", admin.token()).body();
        assertThat(teachers).contains("Tara Teacher").contains("Demo School One").contains("Manoj Manager");

        Signed manoj = signIn(null, "9800000003", "Password123!");
        assertThat(total(get("/api/v1/schools", manoj.token()))).isEqualTo(1);
        // Tara plus the two other demo Teachers placed in Demo School One by the Teacher seeder.
        assertThat(total(get("/api/v1/teachers", manoj.token()))).isEqualTo(3);
    }
    @Test
    void recruitmentDemoDataLeavesReadyRaniReadyToDeployWithTheFunnelFilled() {
        Signed director = signIn(null, "9800000002", "Password123!");

        assertThat(get("/api/v1/induction/ready-to-deploy", director.token()).body()).contains("Ready Rani");
        assertThat(get("/api/v1/recruitment/colleges", director.token()).body()).contains("Demo College of Arts", "Dr. Principal");
        String offers = get("/api/v1/recruitment/offers", director.token()).body();
        assertThat(offers).contains("ACCEPTED", "DECLINED", "ISSUED", "DRAFT");
        assertThat(get("/api/v1/recruitment/dashboard", director.token()).body()).contains("\"interviewed\":6", "\"inducted\":1");
    }
    @Test
    void marketingDemoDataHasProspectsAtSeveralStagesAndAWonSchoolWithAnMou() {
        Signed director = signIn(null, "9800000002", "Password123!");

        String list = get("/api/v1/marketing/prospects?size=100", director.token()).body();
        assertThat(list).contains("Green Valley Public School", "Sunrise Matriculation School", "Lakeview International School", "Hilltop Public School");
        assertThat(list).contains("\"followUpOverdue\":true");
        assertThat(list).contains("\"effectiveStage\":\"ACTIVE\"");
        assertThat(get("/api/v1/marketing/pipeline", director.token()).body()).contains("FINAL_STAGE");
        assertThat(get("/api/v1/marketing/dashboard", director.token()).body()).contains("\"won\":1");
    }

    @Test
    void schoolWorkingDaysDemoRowsAreSeededForTheCurrentYear() {
        Signed admin = signIn(null, "9800000001", "Password123!");

        Resp page = get("/api/v1/school-working-days?size=100", admin.token());
        assertThat(page.status()).isEqualTo(200);
        assertThat(((Number) page.map().get("total")).intValue()).isGreaterThanOrEqualTo(12);
    }

    @Test
    void managerAttendanceDemoDataGivesManojAMonthWithWorkedFieldLeaveAndAbsentDays() {
        Signed manoj = signIn(null, "9800000003", "Password123!");
        Signed director = signIn(null, "9800000002", "Password123!");
        java.time.YearMonth month = java.time.YearMonth.from(java.time.LocalDate.now());

        // the window of eight working days can straddle two months, so look at both
        java.util.List<java.util.Map<String, Object>> days = new java.util.ArrayList<>();
        for (java.time.YearMonth m : new java.time.YearMonth[] {month.minusMonths(1), month}) {
            Resp resp = get("/api/v1/manager-attendance/me/months/" + m, manoj.token());
            assertThat(resp.status()).as(resp.body()).isEqualTo(200);
            @SuppressWarnings("unchecked")
            java.util.List<java.util.Map<String, Object>> found =
                    (java.util.List<java.util.Map<String, Object>>) resp.map().get("days");
            days.addAll(found);
        }
        java.util.List<Object> statuses = days.stream().map(d -> d.get("status")).toList();
        assertThat(statuses).contains("WORKED", "FIELD", "LEAVE", "ABSENT", "NO_CHECK_OUT");

        // the leave went through the real service, so the Director has a pending request to decide
        assertThat(get("/api/v1/manager-attendance/leave?status=PENDING", director.token()).body()).contains("Manoj Manager");
        assertThat(get("/api/v1/manager-attendance/leave?status=APPROVED", director.token()).body()).contains("Family function");
    }

    @Test
    void payrollRunDemoDataLeavesADraftRunForLastMonth() {
        Signed admin = signIn(null, "9800000001", "Password123!");
        String last = java.time.YearMonth.now().minusMonths(1).toString();

        Resp runs = get("/api/v1/payroll/runs?month=" + last, admin.token());

        assertThat(runs.status()).as(runs.body()).isEqualTo(200);
        assertThat(runs.body()).contains("\"month\":\"" + last + "\"").contains("\"status\":\"DRAFT\"");
        // started once: a second start of the same month is refused, so the seeder never duplicates or changes a run
        assertThat(post("/api/v1/payroll/runs", admin.token(), java.util.Map.of("month", last)).status()).isEqualTo(409);
    }

    @Test
    void teacherPerformanceDemoDataHasObservationsAnIssueAndAPlan() {
        Signed director = signIn(null, "9800000002", "Password123!");

        Resp observations = get("/api/v1/teacher-performance/observations?size=100", director.token());
        assertThat(observations.status()).as(observations.body()).isEqualTo(200);
        assertThat(observations.body()).contains("\"overallResult\":\"GOOD\"", "\"overallResult\":\"NEEDS_IMPROVEMENT\"");
        assertThat(get("/api/v1/teacher-performance/issues?view=open", director.token()).body()).contains("UNDER_CORRECTION");
        assertThat(get("/api/v1/teacher-performance/feedback", director.token()).body()).contains("Demo Principal");
        assertThat(get("/api/v1/teacher-performance/plans", director.token()).body()).contains("FOLLOW_UP", "SCHOOL_FEEDBACK");
    }

    @Test
    void expensesDemoDataHasClaimsInEveryStatusAndAnApprovedClaimWaitingForPayroll() {
        Signed manager = signIn(null, "9800000003", "Password123!");

        Resp mine = get("/api/v1/my-expenses?size=100", manager.token());
        assertThat(mine.status()).as(mine.body()).isEqualTo(200);
        assertThat(mine.body()).contains("\"status\":\"DRAFT\"", "\"status\":\"SUBMITTED\"", "\"status\":\"APPROVED\"",
                "\"status\":\"REJECTED\"", "\"status\":\"PAID_BY_COMPANY\"");
        assertThat(mine.map().get("owed")).isNotEqualTo("0.00");
        Signed director = signIn(null, "9800000002", "Password123!");
        assertThat(get("/api/v1/expenses/budgets?month=" + java.time.YearMonth.from(java.time.LocalDate.now().minusDays(1)), director.token()).body())
                .contains("20000.00");
    }

    @SuppressWarnings("unchecked")
    private long tileValue(String token, String area, String key, String period, String label) {
        Resp tile = get("/api/v1/dashboard/areas/" + area + "/tiles/" + key + "?period=" + period, token);
        assertThat(tile.status()).as(tile.body()).isEqualTo(200);
        assertThat(tile.map().get("state")).as(key + " " + tile.body()).isEqualTo("OK");
        return ((java.util.List<java.util.Map<String, Object>>) tile.map().get("values")).stream()
                .filter(v -> label == null || label.equals(v.get("label")))
                .map(v -> ((Number) v.get("value")).longValue())
                .findFirst()
                .orElseThrow();
    }

    /** Spec 028 quickstart prerequisites: every dashboard scenario has demo data behind it. */
    @Test
    void dashboardDemoDataCoversOverdueMissedVisitPendingLeaveAbsentTeacherAndRunAwaitingApproval() {
        Signed director = signIn(null, "9800000002", "Password123!");
        String token = director.token();
        java.time.LocalDate today = java.time.LocalDate.now();
        String month = java.time.YearMonth.from(today).toString();

        assertThat(tileValue(token, "MANAGERS", "managers.tasks.overdue", month, null)).isGreaterThanOrEqualTo(1);
        assertThat(tileValue(token, "MANAGERS", "managers.pendingRequests", month, "Leave")).isGreaterThanOrEqualTo(1);
        String visitMonth = java.time.YearMonth.from(today.minusDays(2)).toString();
        assertThat(tileValue(token, "SCHOOLS", "schools.visits", visitMonth, "Missed")).isGreaterThanOrEqualTo(1);

        Resp inbox = get("/api/v1/dashboard/inbox?kind=PAYROLL_RUN", token);
        assertThat(inbox.status()).as(inbox.body()).isEqualTo(200);
        assertThat(inbox.body()).contains("PAYROLL_RUN");

        // the absence is marked on working days only; on a Sunday or holiday the tile says so instead
        if (today.getDayOfWeek() != java.time.DayOfWeek.SUNDAY) {
            Resp away = get("/api/v1/dashboard/areas/ATTENDANCE_LEAVE/tiles/attendance.awayToday", token);
            assertThat(away.status()).as(away.body()).isEqualTo(200);
            assertThat(away.map().get("state")).isIn("OK", "NOT_WORKING_DAY");
            if ("OK".equals(away.map().get("state"))) {
                assertThat(tileValue(token, "TEACHERS", "operations.substitutions", month, "Open")).isGreaterThanOrEqualTo(1);
            }
        }
    }

    @Test
    void schoolBillingDemoDataHasThisMonthsDueAndAPartCollection() {
        Signed admin = signIn(null, "9800000001", "Password123!");
        String month = java.time.YearMonth.now(java.time.ZoneId.of("Asia/Kolkata")).toString();

        String overview = get("/api/v1/school-billing/overview?month=" + month, admin.token()).body();

        assertThat(overview).contains("Demo School One").contains("\"collected\":20000");
    }
}
