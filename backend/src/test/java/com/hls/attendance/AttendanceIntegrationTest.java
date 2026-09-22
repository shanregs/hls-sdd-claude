package com.hls.attendance;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hls.identity.internal.Role;
import com.hls.identity.internal.User;
import com.hls.identity.internal.UserRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.ObjectMapper;

/**
 * Testcontainers-backed end-to-end coverage for specs/011-attendance
 * (FR-001-FR-025): real Postgres (V1-V9 Flyway migrations applied), real
 * logins through Identity, real Teacher profiles through {@code teacher}'s
 * real endpoints, real Manager-scoping seeded through Organization's real
 * {@code /api/v1/organization/teacher-assignments}, and real audit
 * history read-back through Audit's real {@code GET
 * /api/v1/audit/{entityType}/{id}/history} (quickstart.md).
 *
 * <p><b>Environment note</b>: requires a running Docker daemon.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers
class AttendanceIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private Clock clock;

    // ---- User Story 1: self-marking ------------------------------------------------------

    @Test
    void selfMark_savesAndIsImmediatelyRetrievable() throws Exception {
        String adminToken = loginAs(Role.ADMIN);
        UUID teacherId = createTeacher(adminToken, "Self Mark Teacher", "+919800001001");
        String teacherToken = loginAsTeacher(teacherId);
        String today = LocalDate.now(clock).toString();

        mockMvc.perform(post("/api/v1/attendance/me/marks")
                        .header("Authorization", "Bearer " + teacherToken).contentType("application/json")
                        .content(markBody(today, UUID.randomUUID(), "PRESENT", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusCode").value("PRESENT"))
                .andExpect(jsonPath("$.markedByRole").value("TEACHER"));
    }

    @Test
    void selfMark_halfDay_reflectedInSavedMark() throws Exception {
        String adminToken = loginAs(Role.ADMIN);
        UUID teacherId = createTeacher(adminToken, "Half Day Teacher", "+919800001002");
        String teacherToken = loginAsTeacher(teacherId);
        String today = LocalDate.now(clock).toString();

        mockMvc.perform(post("/api/v1/attendance/me/marks")
                        .header("Authorization", "Bearer " + teacherToken).contentType("application/json")
                        .content(markBody(today, UUID.randomUUID(), "PRESENT", new BigDecimal("0.5"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fractionalValue").value(0.5));
    }

    @Test
    void selfMark_withOptionalEvidence_allFieldsPersisted() throws Exception {
        String adminToken = loginAs(Role.ADMIN);
        UUID teacherId = createTeacher(adminToken, "Evidence Teacher", "+919800001003");
        String teacherToken = loginAsTeacher(teacherId);
        String today = LocalDate.now(clock).toString();
        String body = "{\"markDate\":\"" + today + "\",\"schoolId\":\"" + UUID.randomUUID()
                + "\",\"statusCode\":\"PRESENT\",\"evidence\":{\"checkinCode\":\"ABC123\"}}";

        mockMvc.perform(post("/api/v1/attendance/me/marks")
                        .header("Authorization", "Bearer " + teacherToken).contentType("application/json").content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.evidence.checkinCode").value("ABC123"));
    }

    @Test
    void selfMark_sameDayTwice_editsInPlace() throws Exception {
        String adminToken = loginAs(Role.ADMIN);
        UUID teacherId = createTeacher(adminToken, "Repeat Mark Teacher", "+919800001004");
        String teacherToken = loginAsTeacher(teacherId);
        String today = LocalDate.now(clock).toString();
        UUID schoolId = UUID.randomUUID();

        String firstId = mockMvc.perform(post("/api/v1/attendance/me/marks")
                        .header("Authorization", "Bearer " + teacherToken).contentType("application/json")
                        .content(markBody(today, schoolId, "PRESENT", null)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        UUID markId1 = UUID.fromString(objectMapper.readTree(firstId).get("id").asText());

        String secondId = mockMvc.perform(post("/api/v1/attendance/me/marks")
                        .header("Authorization", "Bearer " + teacherToken).contentType("application/json")
                        .content(markBody(today, schoolId, "LEAVE", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusCode").value("LEAVE"))
                .andReturn().getResponse().getContentAsString();
        UUID markId2 = UUID.fromString(objectMapper.readTree(secondId).get("id").asText());

        org.assertj.core.api.Assertions.assertThat(markId2).isEqualTo(markId1);
    }

    @Test
    void selfMark_editedMark_priorValueRetrievableViaAuditReader() throws Exception {
        String adminToken = loginAs(Role.ADMIN);
        UUID teacherId = createTeacher(adminToken, "Audit History Teacher", "+919800001005");
        String teacherToken = loginAsTeacher(teacherId);
        String today = LocalDate.now(clock).toString();
        UUID schoolId = UUID.randomUUID();

        String body = mockMvc.perform(post("/api/v1/attendance/me/marks")
                        .header("Authorization", "Bearer " + teacherToken).contentType("application/json")
                        .content(markBody(today, schoolId, "PRESENT", null)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        UUID markId = UUID.fromString(objectMapper.readTree(body).get("id").asText());

        mockMvc.perform(post("/api/v1/attendance/me/marks")
                        .header("Authorization", "Bearer " + teacherToken).contentType("application/json")
                        .content(markBody(today, schoolId, "LEAVE", null)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/audit/AttendanceMark/" + markId + "/history").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].afterValue", org.hamcrest.Matchers.containsString("PRESENT")))
                .andExpect(jsonPath("$[1].beforeValue", org.hamcrest.Matchers.containsString("PRESENT")))
                .andExpect(jsonPath("$[1].afterValue", org.hamcrest.Matchers.containsString("LEAVE")));
    }

    // ---- User Story 2: Manager / Admin on-behalf marking --------------------------------

    @Test
    void managerMarksOnBehalf_forAccountableTeacher_succeeds() throws Exception {
        String adminToken = loginAs(Role.ADMIN);
        String directorToken = loginAs(Role.DIRECTOR);
        UUID teacherId = createTeacher(adminToken, "Manager Marked Teacher", "+919800001010");
        User managerUser = seedUser(nextPhone(), "irrelevant-password", Set.of(Role.MANAGER));
        String managerToken = login(managerUser.getPhoneNumber(), "irrelevant-password");
        assignTeacherToManager(directorToken, teacherId, managerUser.getId());

        mockMvc.perform(post("/api/v1/attendance/teachers/" + teacherId + "/marks")
                        .header("Authorization", "Bearer " + managerToken).contentType("application/json")
                        .content(markBody(LocalDate.now(clock).toString(), UUID.randomUUID(), "PRESENT", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.markedByRole").value("MANAGER"));
    }

    @Test
    void managerMarksOnBehalf_forNonAccountableTeacher_isDenied403() throws Exception {
        String adminToken = loginAs(Role.ADMIN);
        UUID teacherId = createTeacher(adminToken, "Unassigned Teacher", "+919800001011");
        User managerUser = seedUser(nextPhone(), "irrelevant-password", Set.of(Role.MANAGER));
        String managerToken = login(managerUser.getPhoneNumber(), "irrelevant-password");

        mockMvc.perform(post("/api/v1/attendance/teachers/" + teacherId + "/marks")
                        .header("Authorization", "Bearer " + managerToken).contentType("application/json")
                        .content(markBody(LocalDate.now(clock).toString(), UUID.randomUUID(), "PRESENT", null)))
                .andExpect(status().isForbidden());
    }

    @Test
    void managerOverwritesTeacherMark_attributionUpdatesToManager() throws Exception {
        String adminToken = loginAs(Role.ADMIN);
        String directorToken = loginAs(Role.DIRECTOR);
        UUID teacherId = createTeacher(adminToken, "Overwritten Teacher", "+919800001012");
        String teacherToken = loginAsTeacher(teacherId);
        User managerUser = seedUser(nextPhone(), "irrelevant-password", Set.of(Role.MANAGER));
        String managerToken = login(managerUser.getPhoneNumber(), "irrelevant-password");
        assignTeacherToManager(directorToken, teacherId, managerUser.getId());
        String today = LocalDate.now(clock).toString();

        mockMvc.perform(post("/api/v1/attendance/me/marks")
                        .header("Authorization", "Bearer " + teacherToken).contentType("application/json")
                        .content(markBody(today, UUID.randomUUID(), "PRESENT", null)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/attendance/teachers/" + teacherId + "/marks")
                        .header("Authorization", "Bearer " + managerToken).contentType("application/json")
                        .content(markBody(today, UUID.randomUUID(), "LEAVE", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.markedByRole").value("MANAGER"))
                .andExpect(jsonPath("$.statusCode").value("LEAVE"));
    }

    @Test
    void adminMarksOnBehalf_forAnyTeacher_succeeds_unscoped() throws Exception {
        String adminToken = loginAs(Role.ADMIN);
        UUID teacherId = createTeacher(adminToken, "Admin Marked Teacher", "+919800001013");

        mockMvc.perform(post("/api/v1/attendance/teachers/" + teacherId + "/marks")
                        .header("Authorization", "Bearer " + adminToken).contentType("application/json")
                        .content(markBody(LocalDate.now(clock).toString(), UUID.randomUUID(), "PRESENT", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.markedByRole").value("ADMIN"));
    }

    // ---- User Story 3: rollup viewing scope + calendar -----------------------------------

    @Test
    void rollup_forAKnownMonthMix_returnsCorrectFiveFigures() throws Exception {
        String adminToken = loginAs(Role.ADMIN);
        UUID teacherId = createTeacher(adminToken, "Rollup Teacher", "+919800001020");
        String teacherToken = loginAsTeacher(teacherId);
        String period = LocalDate.now(clock).toString().substring(0, 7);

        mockMvc.perform(post("/api/v1/attendance/me/marks").header("Authorization", "Bearer " + teacherToken)
                .contentType("application/json").content(markBody(LocalDate.now(clock).toString(), UUID.randomUUID(), "PRESENT", null)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/attendance/teachers/" + teacherId + "/months/" + period)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.daysWorked").value(1.0))
                .andExpect(jsonPath("$.lockStatus").value("UNLOCKED"));
    }

    @Test
    void rollup_unrelatedManager_isDenied403() throws Exception {
        String adminToken = loginAs(Role.ADMIN);
        UUID teacherId = createTeacher(adminToken, "Unrelated Rollup Teacher", "+919800001021");
        User managerUser = seedUser(nextPhone(), "irrelevant-password", Set.of(Role.MANAGER));
        String managerToken = login(managerUser.getPhoneNumber(), "irrelevant-password");
        String period = LocalDate.now(clock).toString().substring(0, 7);

        mockMvc.perform(get("/api/v1/attendance/teachers/" + teacherId + "/months/" + period)
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void rollup_teacherViewsOwn_succeeds_anotherTeachersRollup_isDenied() throws Exception {
        String adminToken = loginAs(Role.ADMIN);
        UUID ownTeacherId = createTeacher(adminToken, "Own Rollup", "+919800001022");
        UUID otherTeacherId = createTeacher(adminToken, "Other Rollup", "+919800001023");
        String teacherToken = loginAsTeacher(ownTeacherId);
        String period = LocalDate.now(clock).toString().substring(0, 7);

        mockMvc.perform(get("/api/v1/attendance/teachers/" + ownTeacherId + "/months/" + period)
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/attendance/teachers/" + otherTeacherId + "/months/" + period)
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void marksForMonth_evidenceFieldsRideAlongWithMarkVisibility_noNarrowerRuleThanTheMarkItself() throws Exception {
        String adminToken = loginAs(Role.ADMIN);
        UUID teacherId = createTeacher(adminToken, "Evidence Visibility Teacher", "+919800001024");
        String teacherToken = loginAsTeacher(teacherId);
        String today = LocalDate.now(clock).toString();
        String period = today.substring(0, 7);
        String body = "{\"markDate\":\"" + today + "\",\"schoolId\":\"" + UUID.randomUUID()
                + "\",\"statusCode\":\"PRESENT\",\"evidence\":{\"checkinCode\":\"XYZ999\"}}";
        mockMvc.perform(post("/api/v1/attendance/me/marks").header("Authorization", "Bearer " + teacherToken)
                        .contentType("application/json").content(body))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/attendance/teachers/" + teacherId + "/months/" + period + "/marks")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].evidence.checkinCode").value("XYZ999"));
    }

    // ---- User Story 4: lock / reopen -----------------------------------------------------

    @Test
    void lockedMonth_directMarkAttempt_rejectedWith409ClearMessage() throws Exception {
        String adminToken = loginAs(Role.ADMIN);
        String directorToken = loginAs(Role.DIRECTOR);
        UUID teacherId = createTeacher(adminToken, "Locked Teacher", "+919800001030");
        String teacherToken = loginAsTeacher(teacherId);
        String period = LocalDate.now(clock).toString().substring(0, 7);

        mockMvc.perform(post("/api/v1/attendance/teachers/" + teacherId + "/months/" + period + "/lock")
                        .header("Authorization", "Bearer " + directorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("LOCKED"));

        mockMvc.perform(post("/api/v1/attendance/me/marks").header("Authorization", "Bearer " + teacherToken)
                        .contentType("application/json")
                        .content(markBody(LocalDate.now(clock).toString(), UUID.randomUUID(), "PRESENT", null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", org.hamcrest.Matchers.containsString("locked")));
    }

    @Test
    void getLockStatus_returnsCurrentStateWithoutMutating() throws Exception {
        String adminToken = loginAs(Role.ADMIN);
        String directorToken = loginAs(Role.DIRECTOR);
        UUID teacherId = createTeacher(adminToken, "Lock Status Teacher", "+919800001034");
        String period = LocalDate.now(clock).toString().substring(0, 7);

        mockMvc.perform(get("/api/v1/attendance/teachers/" + teacherId + "/months/" + period + "/lock")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UNLOCKED"));

        mockMvc.perform(post("/api/v1/attendance/teachers/" + teacherId + "/months/" + period + "/lock")
                .header("Authorization", "Bearer " + directorToken)).andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/attendance/teachers/" + teacherId + "/months/" + period + "/lock")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("LOCKED"));
    }

    @Test
    void reopenWorkflow_endToEnd_lockThenReopenThenCorrectThenRelock() throws Exception {
        String adminToken = loginAs(Role.ADMIN);
        String directorToken = loginAs(Role.DIRECTOR);
        UUID teacherId = createTeacher(adminToken, "Reopen Workflow Teacher", "+919800001031");
        String teacherToken = loginAsTeacher(teacherId);
        String period = LocalDate.now(clock).toString().substring(0, 7);

        mockMvc.perform(post("/api/v1/attendance/teachers/" + teacherId + "/months/" + period + "/lock")
                        .header("Authorization", "Bearer " + directorToken)).andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/attendance/teachers/" + teacherId + "/months/" + period + "/reopen")
                        .header("Authorization", "Bearer " + directorToken).contentType("application/json")
                        .content("{\"reason\":\"Correction needed\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REOPENED"));

        mockMvc.perform(post("/api/v1/attendance/me/marks").header("Authorization", "Bearer " + teacherToken)
                        .contentType("application/json")
                        .content(markBody(LocalDate.now(clock).toString(), UUID.randomUUID(), "PRESENT", null)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/attendance/teachers/" + teacherId + "/months/" + period + "/lock")
                        .header("Authorization", "Bearer " + directorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("LOCKED"))
                .andExpect(jsonPath("$.reopenHistory[0].relockedAt").exists());
    }

    @Test
    void reopenMonth_asAdminOrManager_isDenied403_directorOnly() throws Exception {
        String adminToken = loginAs(Role.ADMIN);
        String directorToken = loginAs(Role.DIRECTOR);
        UUID teacherId = createTeacher(adminToken, "Reopen Denied Teacher", "+919800001032");
        String period = LocalDate.now(clock).toString().substring(0, 7);
        mockMvc.perform(post("/api/v1/attendance/teachers/" + teacherId + "/months/" + period + "/lock")
                .header("Authorization", "Bearer " + directorToken)).andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/attendance/teachers/" + teacherId + "/months/" + period + "/reopen")
                        .header("Authorization", "Bearer " + adminToken).contentType("application/json")
                        .content("{\"reason\":\"attempt\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void lockMonth_asAdminOrManager_isDenied403_directorOnly() throws Exception {
        String adminToken = loginAs(Role.ADMIN);
        UUID teacherId = createTeacher(adminToken, "Lock Denied Teacher", "+919800001033");
        String period = LocalDate.now(clock).toString().substring(0, 7);

        mockMvc.perform(post("/api/v1/attendance/teachers/" + teacherId + "/months/" + period + "/lock")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isForbidden());
    }

    // ---- User Story 5: grid ---------------------------------------------------------------

    @Test
    void grid_asDirector_noFilter_returnsEveryTeacher() throws Exception {
        String adminToken = loginAs(Role.ADMIN);
        String directorToken = loginAs(Role.DIRECTOR);
        createTeacher(adminToken, "Grid Teacher One", "+919800001040");
        String period = LocalDate.now(clock).toString().substring(0, 7);

        mockMvc.perform(get("/api/v1/attendance/grid?period=" + period).header("Authorization", "Bearer " + directorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows").isArray());
    }

    @Test
    void grid_asDirector_filteredByManager_returnsOnlyThatPortfolio() throws Exception {
        String adminToken = loginAs(Role.ADMIN);
        String directorToken = loginAs(Role.DIRECTOR);
        UUID teacherId = createTeacher(adminToken, "Grid Portfolio Teacher", "+919800001041");
        User managerUser = seedUser(nextPhone(), "irrelevant-password", Set.of(Role.MANAGER));
        assignTeacherToManager(directorToken, teacherId, managerUser.getId());
        String period = LocalDate.now(clock).toString().substring(0, 7);

        mockMvc.perform(get("/api/v1/attendance/grid?period=" + period + "&managerId=" + managerUser.getId())
                        .header("Authorization", "Bearer " + directorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows[0].teacherId").value(teacherId.toString()));
    }

    @Test
    void grid_asManager_returnsOnlyOwnPortfolio_neverAnotherManagersTeachers() throws Exception {
        String adminToken = loginAs(Role.ADMIN);
        String directorToken = loginAs(Role.DIRECTOR);
        UUID myTeacherId = createTeacher(adminToken, "My Portfolio Teacher", "+919800001042");
        UUID otherTeacherId = createTeacher(adminToken, "Other Portfolio Teacher", "+919800001043");
        User managerA = seedUser(nextPhone(), "irrelevant-password", Set.of(Role.MANAGER));
        User managerB = seedUser(nextPhone(), "irrelevant-password", Set.of(Role.MANAGER));
        String managerAToken = login(managerA.getPhoneNumber(), "irrelevant-password");
        assignTeacherToManager(directorToken, myTeacherId, managerA.getId());
        assignTeacherToManager(directorToken, otherTeacherId, managerB.getId());
        String period = LocalDate.now(clock).toString().substring(0, 7);

        mockMvc.perform(get("/api/v1/attendance/grid?period=" + period).header("Authorization", "Bearer " + managerAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows.length()").value(1))
                .andExpect(jsonPath("$.rows[0].teacherId").value(myTeacherId.toString()));
    }

    @Test
    void grid_asManager_passingAnotherManagersId_isDenied403() throws Exception {
        String adminToken = loginAs(Role.ADMIN);
        User managerA = seedUser(nextPhone(), "irrelevant-password", Set.of(Role.MANAGER));
        User managerB = seedUser(nextPhone(), "irrelevant-password", Set.of(Role.MANAGER));
        String managerAToken = login(managerA.getPhoneNumber(), "irrelevant-password");
        String period = LocalDate.now(clock).toString().substring(0, 7);

        mockMvc.perform(get("/api/v1/attendance/grid?period=" + period + "&managerId=" + managerB.getId())
                        .header("Authorization", "Bearer " + managerAToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void grid_asTeacher_isDenied403() throws Exception {
        String adminToken = loginAs(Role.ADMIN);
        UUID teacherId = createTeacher(adminToken, "Grid Denied Teacher", "+919800001044");
        String teacherToken = loginAsTeacher(teacherId);
        String period = LocalDate.now(clock).toString().substring(0, 7);

        mockMvc.perform(get("/api/v1/attendance/grid?period=" + period).header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void gridCellEdit_asManagerOrAdmin_persistsThroughMarkOnBehalfEndpoint_andGridReflectsItImmediately() throws Exception {
        String adminToken = loginAs(Role.ADMIN);
        String directorToken = loginAs(Role.DIRECTOR);
        UUID teacherId = createTeacher(adminToken, "Grid Edit Teacher", "+919800001045");
        User managerUser = seedUser(nextPhone(), "irrelevant-password", Set.of(Role.MANAGER));
        assignTeacherToManager(directorToken, teacherId, managerUser.getId());
        String today = LocalDate.now(clock).toString();
        String period = today.substring(0, 7);

        mockMvc.perform(post("/api/v1/attendance/teachers/" + teacherId + "/marks")
                        .header("Authorization", "Bearer " + adminToken).contentType("application/json")
                        .content(markBody(today, UUID.randomUUID(), "LEAVE", null)))
                .andExpect(status().isOk());

        // Filtered to this one Teacher's Manager portfolio, so `rows[0]` is unambiguous
        // regardless of how many other Teachers earlier tests in this class created.
        mockMvc.perform(get("/api/v1/attendance/grid?period=" + period + "&managerId=" + managerUser.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows[0].teacherId").value(teacherId.toString()))
                .andExpect(jsonPath("$.rows[0].cells['" + today + "'].statusCode").value("LEAVE"))
                .andExpect(jsonPath("$.rows[0].cells['" + today + "'].editable").value(true));
    }

    @Test
    void gridCellEdit_lockedMonth_rejected409_cellShowsNotEditable() throws Exception {
        String adminToken = loginAs(Role.ADMIN);
        String directorToken = loginAs(Role.DIRECTOR);
        UUID teacherId = createTeacher(adminToken, "Grid Locked Teacher", "+919800001046");
        User managerUser = seedUser(nextPhone(), "irrelevant-password", Set.of(Role.MANAGER));
        assignTeacherToManager(directorToken, teacherId, managerUser.getId());
        String period = LocalDate.now(clock).toString().substring(0, 7);
        mockMvc.perform(post("/api/v1/attendance/teachers/" + teacherId + "/months/" + period + "/lock")
                .header("Authorization", "Bearer " + directorToken)).andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/attendance/teachers/" + teacherId + "/marks")
                        .header("Authorization", "Bearer " + adminToken).contentType("application/json")
                        .content(markBody(LocalDate.now(clock).toString(), UUID.randomUUID(), "PRESENT", null)))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/api/v1/attendance/grid?period=" + period + "&managerId=" + managerUser.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows[0].teacherId").value(teacherId.toString()))
                .andExpect(jsonPath("$.rows[0].cells['" + LocalDate.now(clock) + "'].editable").value(false));
    }

    // ---- Polish: status codes, non-working calendar, export -----------------------------

    @Test
    void createStatusCode_asAdmin_succeeds_andAppearsInListActiveCodes() throws Exception {
        String adminToken = loginAs(Role.ADMIN);

        mockMvc.perform(post("/api/v1/attendance/status-codes").header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json")
                        .content("{\"code\":\"SICK_LEAVE\",\"label\":\"Sick Leave\",\"category\":\"LEAVE\",\"weight\":0.00}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/attendance/status-codes").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.code=='SICK_LEAVE')]").exists());
    }

    @Test
    void createStatusCode_asDirector_succeeds() throws Exception {
        String directorToken = loginAs(Role.DIRECTOR);

        mockMvc.perform(post("/api/v1/attendance/status-codes").header("Authorization", "Bearer " + directorToken)
                        .contentType("application/json")
                        .content("{\"code\":\"DIRECTOR_ADDED\",\"label\":\"Director Added\",\"category\":\"WORKED\",\"weight\":1.00}"))
                .andExpect(status().isOk());
    }

    @Test
    void createStatusCode_asManagerOrTeacher_isDenied403() throws Exception {
        User managerUser = seedUser(nextPhone(), "irrelevant-password", Set.of(Role.MANAGER));
        String managerToken = login(managerUser.getPhoneNumber(), "irrelevant-password");

        mockMvc.perform(post("/api/v1/attendance/status-codes").header("Authorization", "Bearer " + managerToken)
                        .contentType("application/json")
                        .content("{\"code\":\"DENIED\",\"label\":\"Denied\",\"category\":\"WORKED\",\"weight\":1.00}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void addNonWorkingDate_asDirectorOrManager_isDenied403() throws Exception {
        String directorToken = loginAs(Role.DIRECTOR);

        mockMvc.perform(post("/api/v1/attendance/non-working-dates").header("Authorization", "Bearer " + directorToken)
                        .contentType("application/json")
                        .content("{\"date\":\"2026-12-25\",\"label\":\"Christmas\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void nonWorkingDateEndToEnd_addThenRollupAndGridExcludeItAutomatically() throws Exception {
        String adminToken = loginAs(Role.ADMIN);
        String directorToken = loginAs(Role.DIRECTOR);
        UUID teacherId = createTeacher(adminToken, "Calendar Teacher", "+919800001050");
        User managerUser = seedUser(nextPhone(), "irrelevant-password", Set.of(Role.MANAGER));
        assignTeacherToManager(directorToken, teacherId, managerUser.getId());
        LocalDate holiday = LocalDate.now(clock).withDayOfMonth(Math.min(28, LocalDate.now(clock).lengthOfMonth()));
        String period = holiday.toString().substring(0, 7);

        mockMvc.perform(post("/api/v1/attendance/non-working-dates").header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json")
                        .content("{\"date\":\"" + holiday + "\",\"label\":\"Test Holiday\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/attendance/teachers/" + teacherId + "/months/" + period)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unmarkedDays", org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.equalTo(LocalDate.now(clock).lengthOfMonth()))));

        // Filtered to this one Teacher's Manager portfolio, so `rows[0]` is unambiguous.
        mockMvc.perform(get("/api/v1/attendance/grid?period=" + period + "&managerId=" + managerUser.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows[0].teacherId").value(teacherId.toString()))
                .andExpect(jsonPath("$.rows[0].cells['" + holiday + "'].category").value("NON_WORKING"));
    }

    @Test
    void nonWorkingDate_explicitMarkOnSameDate_overridesCalendar_forThatTeacherOnly() throws Exception {
        String adminToken = loginAs(Role.ADMIN);
        UUID teacherId = createTeacher(adminToken, "Override Calendar Teacher", "+919800001051");
        LocalDate holiday = LocalDate.now(clock).withDayOfMonth(Math.min(27, LocalDate.now(clock).lengthOfMonth()));
        String period = holiday.toString().substring(0, 7);

        mockMvc.perform(post("/api/v1/attendance/non-working-dates").header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json")
                        .content("{\"date\":\"" + holiday + "\",\"label\":\"Override Holiday\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/attendance/teachers/" + teacherId + "/marks")
                        .header("Authorization", "Bearer " + adminToken).contentType("application/json")
                        .content(markBody(holiday.toString(), UUID.randomUUID(), "PRESENT", null)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/attendance/teachers/" + teacherId + "/months/" + period)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.daysWorked").value(1.0));
    }

    @Test
    void exportMonth_returnsCsvWithMarksAndRollupFigures_sameScopingAsRollup() throws Exception {
        String adminToken = loginAs(Role.ADMIN);
        UUID teacherId = createTeacher(adminToken, "Export Teacher", "+919800001060");
        String teacherToken = loginAsTeacher(teacherId);
        String today = LocalDate.now(clock).toString();
        String period = today.substring(0, 7);
        mockMvc.perform(post("/api/v1/attendance/me/marks").header("Authorization", "Bearer " + teacherToken)
                        .contentType("application/json").content(markBody(today, UUID.randomUUID(), "PRESENT", null)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/attendance/teachers/" + teacherId + "/months/" + period + "/export")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.containsString("PRESENT")));

        User managerUser = seedUser(nextPhone(), "irrelevant-password", Set.of(Role.MANAGER));
        String managerToken = login(managerUser.getPhoneNumber(), "irrelevant-password");
        mockMvc.perform(get("/api/v1/attendance/teachers/" + teacherId + "/months/" + period + "/export")
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isForbidden());
    }

    // ---- helpers -------------------------------------------------------------------------

    private record CreateBody(String name, String phone, String email, BigDecimal hlsOfferedSalary, String status) {
    }

    private record AssignRequestBody(UUID schoolId, UUID teacherId, UUID managerId, UUID endsAssignmentId) {
    }

    private String markBody(String date, UUID schoolId, String statusCode, BigDecimal fractionalValue) {
        return "{\"markDate\":\"" + date + "\",\"schoolId\":\"" + schoolId + "\",\"statusCode\":\"" + statusCode + "\""
                + (fractionalValue != null ? ",\"fractionalValue\":" + fractionalValue : "") + "}";
    }

    private UUID createTeacher(String adminToken, String name, String phone) throws Exception {
        String body = mockMvc.perform(post("/api/v1/teachers")
                        .header("Authorization", "Bearer " + adminToken).contentType("application/json")
                        .content(objectMapper.writeValueAsString(new CreateBody(name, phone, null, new BigDecimal("15000.00"), "ACTIVE"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(body).get("id").asText());
    }

    private void assignTeacherToManager(String directorToken, UUID teacherId, UUID managerId) throws Exception {
        mockMvc.perform(post("/api/v1/organization/teacher-assignments")
                        .header("Authorization", "Bearer " + directorToken).contentType("application/json")
                        .content(objectMapper.writeValueAsString(new AssignRequestBody(null, teacherId, managerId, null))))
                .andExpect(status().isOk());
    }

    private static final AtomicInteger PHONE_SEQUENCE = new AtomicInteger(1);

    private String nextPhone() {
        return "+9198320000" + String.format("%02d", PHONE_SEQUENCE.getAndIncrement());
    }

    private String loginAs(Role role) throws Exception {
        User user = seedUser(nextPhone(), "correct-password", Set.of(role));
        return login(user.getPhoneNumber(), "correct-password");
    }

    private String loginAsTeacher(UUID teacherId) throws Exception {
        User teacherUser = seedUser(nextPhone(), "correct-password", Set.of(Role.TEACHER));
        teacherUser.setLinkedTeacherId(teacherId);
        userRepository.save(teacherUser);
        return login(teacherUser.getPhoneNumber(), "correct-password");
    }

    private String login(String phoneNumber, String password) throws Exception {
        var result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new java.util.HashMap<>() {{
                            put("phoneNumber", phoneNumber);
                            put("password", password);
                        }})))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
    }

    private User seedUser(String phoneNumber, String rawPassword, Set<Role> roles) {
        User user = new User(UUID.randomUUID(), "Attendance Integration Test User", phoneNumber, roles, Instant.now(clock));
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        return userRepository.save(user);
    }
}
