package com.hls.teacher;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hls.identity.internal.Role;
import com.hls.identity.internal.User;
import com.hls.identity.internal.UserRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
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
 * Testcontainers-backed end-to-end coverage: real Postgres (V1-V6 Flyway
 * migrations applied), real logins through Identity's
 * {@code /api/v1/auth/login} to obtain bearer tokens, real HTTP calls
 * against {@code TeacherController}, real Manager-scoping seeded through
 * Organization's real {@code /api/v1/organization/teacher-assignments}, and
 * real history read-back through Audit's real
 * {@code GET /api/v1/audit/TeacherProfile/{id}/history} (quickstart.md).
 *
 * <p><b>Environment note</b>: requires a running Docker daemon.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers
class TeacherIntegrationTest {

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

    // ---- User Story 1: create + immediately retrievable, validation, non-Admin denied -----

    @Test
    void createTeacher_thenImmediatelyRetrievable_withExactDetails() throws Exception {
        String adminToken = loginAs(Role.ADMIN);
        String directorToken = loginAs(Role.DIRECTOR);

        String body = mockMvc.perform(post("/api/v1/teachers")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new CreateBody(
                                "Priya Sharma", "+919811111111", "priya@example.com",
                                new BigDecimal("17000.00"), "IN_TRAINING"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Priya Sharma"))
                .andReturn().getResponse().getContentAsString();
        UUID teacherId = UUID.fromString(objectMapper.readTree(body).get("id").asText());

        mockMvc.perform(get("/api/v1/teachers/" + teacherId).header("Authorization", "Bearer " + directorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Priya Sharma"))
                .andExpect(jsonPath("$.phone").value("+919811111111"))
                .andExpect(jsonPath("$.hlsOfferedSalary").value(17000.00))
                .andExpect(jsonPath("$.status").value("IN_TRAINING"));
    }

    @Test
    void createTeacher_asNonAdmin_isDenied() throws Exception {
        String directorToken = loginAs(Role.DIRECTOR);

        mockMvc.perform(post("/api/v1/teachers")
                        .header("Authorization", "Bearer " + directorToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new CreateBody(
                                "Should Be Denied", "+919800000000", null, new BigDecimal("10000"), "IN_TRAINING"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void createTeacher_missingRequiredField_returns400WithFieldNamed() throws Exception {
        String adminToken = loginAs(Role.ADMIN);

        mockMvc.perform(post("/api/v1/teachers")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json")
                        .content("{\"name\":\"No Salary\",\"phone\":\"+919800000001\",\"status\":\"IN_TRAINING\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("hlsOfferedSalary")));
    }

    // ---- User Story 2: update + status change + salary change, all retrievable as real Audit history ----

    @Test
    void updateContact_thenChangeStatus_thenRecordSalaryChange_allRetrievableAsHistory_viaRealAuditEndpoint() throws Exception {
        String adminToken = loginAs(Role.ADMIN);
        UUID teacherId = createTeacher(adminToken, "History Teacher", "+919800000010", "15000.00", "IN_TRAINING");

        mockMvc.perform(patch("/api/v1/teachers/" + teacherId)
                        .header("Authorization", "Bearer " + adminToken).contentType("application/json")
                        .content("{\"email\":\"history-teacher@example.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("history-teacher@example.com"));

        mockMvc.perform(post("/api/v1/teachers/" + teacherId + "/status")
                        .header("Authorization", "Bearer " + adminToken).contentType("application/json")
                        .content("{\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        mockMvc.perform(post("/api/v1/teachers/" + teacherId + "/salary")
                        .header("Authorization", "Bearer " + adminToken).contentType("application/json")
                        .content("{\"amount\":18000.00}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(18000.00));

        mockMvc.perform(get("/api/v1/audit/TeacherProfile/" + teacherId + "/history")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4));
    }

    @Test
    void updateProfile_asNonAdmin_isDenied() throws Exception {
        String adminToken = loginAs(Role.ADMIN);
        String directorToken = loginAs(Role.DIRECTOR);
        UUID teacherId = createTeacher(adminToken, "Deny Update", "+919800000011", "15000.00", "IN_TRAINING");

        mockMvc.perform(patch("/api/v1/teachers/" + teacherId)
                        .header("Authorization", "Bearer " + directorToken).contentType("application/json")
                        .content("{\"email\":\"should-be-denied@example.com\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void changeStatus_asNonAdmin_isDenied() throws Exception {
        String adminToken = loginAs(Role.ADMIN);
        String directorToken = loginAs(Role.DIRECTOR);
        UUID teacherId = createTeacher(adminToken, "Deny Status", "+919800000012", "15000.00", "IN_TRAINING");

        mockMvc.perform(post("/api/v1/teachers/" + teacherId + "/status")
                        .header("Authorization", "Bearer " + directorToken).contentType("application/json")
                        .content("{\"status\":\"ACTIVE\"}"))
                .andExpect(status().isForbidden());
    }

    // ---- User Story 3: Director unscoped, Manager scoped to current assignment ------------

    @Test
    void director_viewsAnyProfile_regardlessOfCurrentAssignment() throws Exception {
        String adminToken = loginAs(Role.ADMIN);
        String directorToken = loginAs(Role.DIRECTOR);
        UUID teacherId = createTeacher(adminToken, "Director View", "+919800000020", "15000.00", "ACTIVE");

        mockMvc.perform(get("/api/v1/teachers/" + teacherId).header("Authorization", "Bearer " + directorToken))
                .andExpect(status().isOk());
    }

    @Test
    void managerCurrentlyAssignedToTeacher_canViewProfile_notAssigned_isDenied() throws Exception {
        String adminToken = loginAs(Role.ADMIN);
        String directorToken = loginAs(Role.DIRECTOR);
        UUID teacherId = createTeacher(adminToken, "Manager View", "+919800000021", "15000.00", "ACTIVE");
        User managerUser = seedUser(nextPhone(), "irrelevant-password", Set.of(Role.MANAGER));
        String managerToken = login(managerUser.getPhoneNumber(), "irrelevant-password");

        // Not yet assigned: denied (also covers the Edge Case of a Manager with zero teachers).
        mockMvc.perform(get("/api/v1/teachers/" + teacherId).header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isForbidden());

        assignTeacherToManager(directorToken, teacherId, managerUser.getId());

        mockMvc.perform(get("/api/v1/teachers/" + teacherId).header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk());
    }

    @Test
    void teacherReassignedFromManagerAToManagerB_accessFollowsTheReassignment() throws Exception {
        String adminToken = loginAs(Role.ADMIN);
        String directorToken = loginAs(Role.DIRECTOR);
        UUID teacherId = createTeacher(adminToken, "Reassigned Teacher", "+919800000022", "15000.00", "ACTIVE");
        User managerA = seedUser(nextPhone(), "irrelevant-password", Set.of(Role.MANAGER));
        User managerB = seedUser(nextPhone(), "irrelevant-password", Set.of(Role.MANAGER));
        String managerAToken = login(managerA.getPhoneNumber(), "irrelevant-password");
        String managerBToken = login(managerB.getPhoneNumber(), "irrelevant-password");

        UUID assignmentId = assignTeacherToManager(directorToken, teacherId, managerA.getId());
        mockMvc.perform(get("/api/v1/teachers/" + teacherId).header("Authorization", "Bearer " + managerAToken))
                .andExpect(status().isOk());

        reassignTeacherToManager(directorToken, teacherId, managerB.getId(), assignmentId);

        mockMvc.perform(get("/api/v1/teachers/" + teacherId).header("Authorization", "Bearer " + managerBToken))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/teachers/" + teacherId).header("Authorization", "Bearer " + managerAToken))
                .andExpect(status().isForbidden());
    }

    // ---- User Story 4: Teacher self-view via /me, denied for another's profile ------------

    @Test
    void teacherViewsOwnProfile_viaMeEndpoint_succeeds() throws Exception {
        String adminToken = loginAs(Role.ADMIN);
        UUID teacherId = createTeacher(adminToken, "Self View Teacher", "+919800000030", "15000.00", "ACTIVE");
        User teacherUser = seedUser(nextPhone(), "correct-password", Set.of(Role.TEACHER));
        teacherUser.setLinkedTeacherId(teacherId);
        userRepository.save(teacherUser);
        String teacherToken = login(teacherUser.getPhoneNumber(), "correct-password");

        mockMvc.perform(get("/api/v1/teachers/me").header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(teacherId.toString()))
                .andExpect(jsonPath("$.name").value("Self View Teacher"));
    }

    @Test
    void teacherAttemptsToViewAnotherTeachersProfile_viaTeacherIdEndpoint_isDenied() throws Exception {
        String adminToken = loginAs(Role.ADMIN);
        UUID ownTeacherId = createTeacher(adminToken, "Own Profile", "+919800000031", "15000.00", "ACTIVE");
        UUID otherTeacherId = createTeacher(adminToken, "Other Profile", "+919800000032", "15000.00", "ACTIVE");
        User teacherUser = seedUser(nextPhone(), "correct-password", Set.of(Role.TEACHER));
        teacherUser.setLinkedTeacherId(ownTeacherId);
        userRepository.save(teacherUser);
        String teacherToken = login(teacherUser.getPhoneNumber(), "correct-password");

        mockMvc.perform(get("/api/v1/teachers/" + ownTeacherId).header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/teachers/" + otherTeacherId).header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void callerWithNoLinkedTeacherId_meEndpoint_returns403() throws Exception {
        String directorToken = loginAs(Role.DIRECTOR);

        mockMvc.perform(get("/api/v1/teachers/me").header("Authorization", "Bearer " + directorToken))
                .andExpect(status().isForbidden());
    }

    // ---- specs/009 User Story 1: initial salary immediately retrievable, current + as-of --

    @Test
    void createTeacher_thenSalaryImmediatelyRetrievable_asCurrentAndAsOfCreationDate() throws Exception {
        String adminToken = loginAs(Role.ADMIN);
        String directorToken = loginAs(Role.DIRECTOR);
        UUID teacherId = createTeacher(adminToken, "Salary Onboard", "+919800000040", "17000.00", "IN_TRAINING");
        String today = java.time.LocalDate.now(clock).toString();

        mockMvc.perform(get("/api/v1/teachers/" + teacherId + "/salary").header("Authorization", "Bearer " + directorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("RECORDED"))
                .andExpect(jsonPath("$.amount").value(17000.00));

        mockMvc.perform(get("/api/v1/teachers/" + teacherId + "/salary?asOf=" + today).header("Authorization", "Bearer " + directorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("RECORDED"))
                .andExpect(jsonPath("$.amount").value(17000.00));
    }

    // ---- specs/009 User Story 2: recording an increment, non-Admin denied, as-of queries --

    @Test
    void recordSalaryChange_asNonAdmin_isDenied() throws Exception {
        String adminToken = loginAs(Role.ADMIN);
        String directorToken = loginAs(Role.DIRECTOR);
        UUID teacherId = createTeacher(adminToken, "Deny Salary", "+919800000041", "15000.00", "ACTIVE");

        mockMvc.perform(post("/api/v1/teachers/" + teacherId + "/salary")
                        .header("Authorization", "Bearer " + directorToken).contentType("application/json")
                        .content("{\"amount\":20000.00}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void recordSalaryChange_thenAsOfQueries_returnCorrectAmountsBeforeAndAfter() throws Exception {
        String adminToken = loginAs(Role.ADMIN);
        String directorToken = loginAs(Role.DIRECTOR);
        UUID teacherId = createTeacher(adminToken, "Increment Teacher", "+919800000042", "17000.00", "ACTIVE");
        java.time.LocalDate incrementDate = java.time.LocalDate.now(clock).plusDays(10);

        mockMvc.perform(post("/api/v1/teachers/" + teacherId + "/salary")
                        .header("Authorization", "Bearer " + adminToken).contentType("application/json")
                        .content("{\"amount\":19000.00,\"effectiveFrom\":\"" + incrementDate + "\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/teachers/" + teacherId + "/salary?asOf=" + incrementDate.minusDays(1))
                        .header("Authorization", "Bearer " + directorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(17000.00));

        mockMvc.perform(get("/api/v1/teachers/" + teacherId + "/salary?asOf=" + incrementDate)
                        .header("Authorization", "Bearer " + directorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(19000.00));
    }

    // ---- specs/009 User Story 3: viewing the profile shows the current salary, no extra step ---

    @Test
    void viewingProfile_afterIncrement_showsNewSalary_withNoExtraStep() throws Exception {
        String adminToken = loginAs(Role.ADMIN);
        String directorToken = loginAs(Role.DIRECTOR);
        UUID teacherId = createTeacher(adminToken, "Profile Reflects Increment", "+919800000043", "17000.00", "ACTIVE");

        mockMvc.perform(post("/api/v1/teachers/" + teacherId + "/salary")
                        .header("Authorization", "Bearer " + adminToken).contentType("application/json")
                        .content("{\"amount\":19000.00}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/teachers/" + teacherId).header("Authorization", "Bearer " + directorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hlsOfferedSalary").value(19000.00));
    }

    // ---- specs/009 User Story 4: as-of before the first entry, and the same viewing rules -

    @Test
    void salaryAsOf_beforeFirstRecordedEntry_returnsNotYetRecorded() throws Exception {
        String adminToken = loginAs(Role.ADMIN);
        String directorToken = loginAs(Role.DIRECTOR);
        UUID teacherId = createTeacher(adminToken, "Not Yet Recorded", "+919800000044", "15000.00", "ACTIVE");

        mockMvc.perform(get("/api/v1/teachers/" + teacherId + "/salary?asOf=2000-01-01")
                        .header("Authorization", "Bearer " + directorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("NOT_YET_RECORDED"))
                .andExpect(jsonPath("$.amount").doesNotExist());
    }

    @Test
    void salaryAsOf_respectsTheSameViewingRulesAsTheProfile() throws Exception {
        String adminToken = loginAs(Role.ADMIN);
        UUID teacherId = createTeacher(adminToken, "Salary Scoped", "+919800000045", "15000.00", "ACTIVE");
        User managerUser = seedUser(nextPhone(), "irrelevant-password", Set.of(Role.MANAGER));
        String managerToken = login(managerUser.getPhoneNumber(), "irrelevant-password");

        mockMvc.perform(get("/api/v1/teachers/" + teacherId + "/salary").header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isForbidden());
    }

    // ---- helpers -------------------------------------------------------------------------

    private record CreateBody(String name, String phone, String email, BigDecimal hlsOfferedSalary, String status) {
    }

    private record AssignRequestBody(UUID schoolId, UUID teacherId, UUID managerId, UUID endsAssignmentId) {
    }

    private UUID createTeacher(String adminToken, String name, String phone, String salary, String status) throws Exception {
        String body = mockMvc.perform(post("/api/v1/teachers")
                        .header("Authorization", "Bearer " + adminToken).contentType("application/json")
                        .content(objectMapper.writeValueAsString(new CreateBody(name, phone, null, new BigDecimal(salary), status))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(body).get("id").asText());
    }

    private UUID assignTeacherToManager(String directorToken, UUID teacherId, UUID managerId) throws Exception {
        String body = mockMvc.perform(post("/api/v1/organization/teacher-assignments")
                        .header("Authorization", "Bearer " + directorToken).contentType("application/json")
                        .content(objectMapper.writeValueAsString(new AssignRequestBody(null, teacherId, managerId, null))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(body).get("id").asText());
    }

    private void reassignTeacherToManager(String directorToken, UUID teacherId, UUID managerId, UUID endsAssignmentId) throws Exception {
        mockMvc.perform(post("/api/v1/organization/teacher-assignments")
                        .header("Authorization", "Bearer " + directorToken).contentType("application/json")
                        .content(objectMapper.writeValueAsString(new AssignRequestBody(null, teacherId, managerId, endsAssignmentId))))
                .andExpect(status().isOk());
    }

    private static final AtomicInteger PHONE_SEQUENCE = new AtomicInteger(1);

    private String nextPhone() {
        return "+9198310000" + String.format("%02d", PHONE_SEQUENCE.getAndIncrement());
    }

    private String loginAs(Role role) throws Exception {
        User user = seedUser(nextPhone(), "correct-password", Set.of(role));
        return login(user.getPhoneNumber(), "correct-password");
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
        User user = new User(UUID.randomUUID(), "Teacher Integration Test User", phoneNumber, roles, Instant.now(clock));
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        return userRepository.save(user);
    }
}
