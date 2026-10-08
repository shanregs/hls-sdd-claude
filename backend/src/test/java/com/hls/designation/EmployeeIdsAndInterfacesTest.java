package com.hls.designation;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.designation.api.EmployeeIds;
import com.hls.designation.api.EmployeeIds.PersonKind;
import com.hls.identity.user.Role;
import com.hls.organization.api.ManagerQueries;
import com.hls.school.api.ConflictException;
import com.hls.support.DesignationTestBase;
import com.hls.teacher.api.TeacherDirectory;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/** SC-003 and SC-007 (T016, T020, T029): the id registry under concurrency and the public interfaces in bulk. */
class EmployeeIdsAndInterfacesTest extends DesignationTestBase {

    @Autowired
    private EmployeeIds employeeIds;

    @Autowired
    private TransactionTemplate transactions;

    @Autowired
    private ManagerQueries managerQueries;

    @Autowired
    private TeacherDirectory teacherDirectory;

    @Autowired
    private SessionFactory sessionFactory;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void twoSimultaneousClaimsOfOneIdLeaveExactlyOneHolder() throws Exception {
        String id = uniqueEmployeeId();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        AtomicInteger refused = new AtomicInteger();
        try {
            List<Callable<Boolean>> claims = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                UUID person = UUID.randomUUID();
                claims.add(() -> {
                    try {
                        transactions.executeWithoutResult(
                                s -> employeeIds.claim(PersonKind.TEACHER, person, "Person " + person, id));
                        return true;
                    } catch (ConflictException e) {
                        refused.incrementAndGet();
                        return false;
                    }
                });
            }
            int won = 0;
            for (Future<Boolean> f : pool.invokeAll(claims)) {
                if (f.get()) {
                    won++;
                }
            }
            assertThat(won).isEqualTo(1);
            assertThat(refused.get()).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
        assertThat(jdbcTemplate.queryForObject(
                        "select count(*) from employee_id_claim where employee_key = ?", Integer.class, id.toLowerCase()))
                .isEqualTo(1);
    }

    @Test
    void theBulkInterfacesReturnWhatTheScreensShowInAFixedNumberOfStatements() {
        String admin = signInAs(Role.ADMIN).token();
        UUID managerKind = designation(admin, "MANAGER");
        UUID teacherKind = designation(admin, "TEACHER");
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));

        List<UUID> managerIds = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            ManagerCtx m = newManager(admin);
            assertThat(employment(admin, m.managerId(), uniqueEmployeeId(), today.minusDays(10), null).status()).isEqualTo(200);
            assertThat(designate(admin, m.managerId(), managerKind, today.minusDays(10)).status()).isEqualTo(200);
            managerIds.add(m.managerId());
        }
        List<UUID> teacherIds = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            UUID t = teacher(admin);
            assertThat(teacherEmployment(admin, t, teacherKind, uniqueEmployeeId()).status()).isEqualTo(200);
            teacherIds.add(t);
        }

        assertThat(managerQueries.designationsOn(managerIds, today)).hasSize(3).containsValue(managerKind);
        assertThat(managerQueries.designationOn(managerIds.get(0), today.minusDays(11))).isEmpty();
        assertThat(managerQueries.designationOn(managerIds.get(0), today)).contains(managerKind);
        assertThat(managerQueries.employment(managerIds)).hasSize(3).allSatisfy((id, e) -> {
            assertThat(e.employeeId()).isNotBlank();
            assertThat(e.joiningDate()).isEqualTo(today.minusDays(10));
            assertThat(e.exitDate()).isNull();
        });
        assertThat(managerQueries.holderCountsByDesignation(List.of(managerKind))).containsEntry(managerKind, 3L);
        assertThat(teacherDirectory.currentDesignations(teacherIds)).hasSize(3).containsValue(teacherKind);
        assertThat(teacherDirectory.currentDesignation(teacherIds.get(0))).contains(teacherKind);
        assertThat(teacherDirectory.employment(teacherIds)).hasSize(3);
        assertThat(teacherDirectory.holderCountsByDesignation(List.of(teacherKind))).containsEntry(teacherKind, 3L);

        // SC-007: the number of statements does not grow with the number of people
        var stats = sessionFactory.getStatistics();
        stats.setStatisticsEnabled(true);
        stats.clear();
        managerQueries.designationsOn(managerIds.subList(0, 1), today);
        long one = stats.getPrepareStatementCount();
        stats.clear();
        managerQueries.designationsOn(managerIds, today);
        assertThat(stats.getPrepareStatementCount()).isEqualTo(one);
        stats.clear();
        teacherDirectory.currentDesignations(teacherIds.subList(0, 1));
        long oneTeacher = stats.getPrepareStatementCount();
        stats.clear();
        teacherDirectory.currentDesignations(teacherIds);
        assertThat(stats.getPrepareStatementCount()).isEqualTo(oneTeacher);
        stats.setStatisticsEnabled(false);
    }

    @Test
    void theSummaryEqualsTheFilteredLists() {
        String admin = signInAs(Role.ADMIN).token();
        UUID teacherKind = designation(admin, "TEACHER");
        UUID t1 = teacher(admin);
        teacher(admin);
        teacherEmployment(admin, t1, teacherKind, null);
        newManager(admin);

        Resp summary = get("/api/v1/designations/summary", admin);
        Map<String, Object> s = summary.map();
        Resp teachers = get("/api/v1/teachers?missingDesignation=true&size=100", admin);
        Resp managers = get("/api/v1/managers?missing=true&size=100", admin);
        assertThat(((Number) s.get("teachersMissingDesignation")).longValue()).isEqualTo(total(teachers));
        assertThat(content(teachers)).allSatisfy(t -> assertThat(t.toString()).contains("DESIGNATION"));
        assertThat(total(managers)).isGreaterThanOrEqualTo(((Number) s.get("managersMissingDesignation")).longValue());
        assertThat(content(managers)).allSatisfy(m -> assertThat(m.get("employment").toString()).contains("missing"));
        assertThat(teachers.body()).doesNotContain(t1.toString());
    }
}
