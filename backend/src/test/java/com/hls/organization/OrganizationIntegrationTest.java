package com.hls.organization;

import tools.jackson.databind.ObjectMapper;
import com.hls.identity.internal.Role;
import com.hls.identity.internal.User;
import com.hls.identity.internal.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Clock;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Testcontainers-backed end-to-end coverage: real Postgres (both {@code V1} and
 * {@code V2} Flyway migrations applied), a real login through Identity's
 * {@code /api/v1/auth/login} to obtain a bearer token, then real HTTP calls
 * against {@code OrganizationController}. Covers tasks.md T011/T018/T025
 * (User Stories 1-3's integration-level acceptance criteria) and
 * quickstart.md's Scenarios 4-5.
 *
 * <p>Imports {@code identity.internal.*} directly for user-seeding, exactly as
 * {@code IdentityIntegrationTest} does within its own module — {@code ArchitectureTest}
 * excludes test sources from its boundary check (research.md's module-boundary
 * rule only constrains {@code main} code).
 *
 * <p><b>Environment note</b>: requires a running Docker daemon.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers
class OrganizationIntegrationTest {

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

    // ---- User Story 1 (T011): assign over real HTTP, then query -------------------------

    @Test
    void assignSchoolManager_overHttp_thenAccountableManagerQueryReturnsIt() throws Exception {
        String accessToken = loginAsDirector();
        UUID schoolId = UUID.randomUUID();
        UUID managerId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/organization/school-assignments")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new AssignmentRequestBody(schoolId, null, managerId, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.managerId").value(managerId.toString()));

        mockMvc.perform(get("/api/v1/organization/schools/" + schoolId + "/accountable-manager")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("CURRENT_MANAGER"))
                .andExpect(jsonPath("$.managerId").value(managerId.toString()));
    }

    // ---- User Story 2 (T018): conflicting reassignment, and FR-013 independence ---------

    @Test
    void reassignment_namingAnAlreadyEndedAssignment_isRejectedAsConflict() throws Exception {
        String accessToken = loginAsDirector();
        UUID schoolId = UUID.randomUUID();
        UUID managerA = UUID.randomUUID();
        UUID managerB = UUID.randomUUID();
        UUID managerC = UUID.randomUUID();

        String firstAssignBody = mockMvc.perform(post("/api/v1/organization/school-assignments")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new AssignmentRequestBody(schoolId, null, managerA, null))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        UUID currentAssignmentId = UUID.fromString(objectMapper.readTree(firstAssignBody).get("id").asText());

        // SC-004: the first reassignment naming this row succeeds...
        mockMvc.perform(post("/api/v1/organization/school-assignments")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new AssignmentRequestBody(schoolId, null, managerB, currentAssignmentId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.managerId").value(managerB.toString()));

        // ...and a second one naming the SAME now-already-ended row is rejected (409),
        // not silently applied on top — the same conditional-UPDATE guard
        // AccountabilityServiceTest already proves at the unit level, exercised here
        // over real HTTP against a real row.
        mockMvc.perform(post("/api/v1/organization/school-assignments")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new AssignmentRequestBody(schoolId, null, managerC, currentAssignmentId))))
                .andExpect(status().isConflict());
    }

    // quickstart.md Scenario 4 / FR-013.
    @Test
    void reassigningSchoolManager_leavesAnUnrelatedTeachersAccountableManagerUnchanged() throws Exception {
        String accessToken = loginAsDirector();
        UUID teacherId = UUID.randomUUID();
        UUID teachersManager = UUID.randomUUID();
        UUID schoolId = UUID.randomUUID();
        UUID schoolManagerA = UUID.randomUUID();
        UUID schoolManagerB = UUID.randomUUID();

        // Assign the Teacher to its own Manager.
        mockMvc.perform(post("/api/v1/organization/teacher-assignments")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new AssignmentRequestBody(null, teacherId, teachersManager, null))))
                .andExpect(status().isOk());

        // Assign, then reassign, an unrelated School's Manager.
        String schoolAssignBody = mockMvc.perform(post("/api/v1/organization/school-assignments")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new AssignmentRequestBody(schoolId, null, schoolManagerA, null))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        UUID schoolAssignmentId = UUID.fromString(objectMapper.readTree(schoolAssignBody).get("id").asText());

        mockMvc.perform(post("/api/v1/organization/school-assignments")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new AssignmentRequestBody(schoolId, null, schoolManagerB, schoolAssignmentId))))
                .andExpect(status().isOk());

        // The Teacher's own accountable Manager must be untouched by the School reassignment.
        mockMvc.perform(get("/api/v1/organization/teachers/" + teacherId + "/accountable-manager")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.managerId").value(teachersManager.toString()));
    }

    // ---- User Story 3 (T025): end-without-replacement appears on unassigned list --------

    // quickstart.md Scenario 5 / SC-005.
    @Test
    void endingAssignmentWithoutReplacement_appearsOnUnassignedListInTheSameSession() throws Exception {
        String accessToken = loginAsDirector();
        UUID schoolId = UUID.randomUUID();
        UUID managerId = UUID.randomUUID();

        String assignBody = mockMvc.perform(post("/api/v1/organization/school-assignments")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new AssignmentRequestBody(schoolId, null, managerId, null))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        UUID assignmentId = UUID.fromString(objectMapper.readTree(assignBody).get("id").asText());

        mockMvc.perform(delete("/api/v1/organization/school-assignments/" + assignmentId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/organization/unassigned?itemType=SCHOOL")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.itemId=='" + schoolId + "')]").exists())
                .andExpect(jsonPath("$[?(@.itemId=='" + schoolId + "')].lastEndedAt").exists());
    }

    // ---- authorization: non-Director/Admin callers are rejected --------------------------

    @Test
    void nonDirectorOrAdminCaller_isDeniedByOrganizationEndpoints() throws Exception {
        User teacher = seedUser("+919820000099", "irrelevant-password", Set.of(Role.TEACHER));
        String accessToken = login(teacher.getPhoneNumber(), "irrelevant-password");

        mockMvc.perform(post("/api/v1/organization/school-assignments")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new AssignmentRequestBody(UUID.randomUUID(), null, UUID.randomUUID(), null))))
                .andExpect(status().isForbidden());
    }

    // ---- helpers -------------------------------------------------------------------------

    private record AssignmentRequestBody(UUID schoolId, UUID teacherId, UUID managerId, UUID endsAssignmentId) {
    }

    private static final AtomicInteger DIRECTOR_SEQUENCE = new AtomicInteger(1);

    private String loginAsDirector() throws Exception {
        String phoneNumber = "+9198300000" + String.format("%02d", DIRECTOR_SEQUENCE.getAndIncrement());
        User director = seedUser(phoneNumber, "correct-password", Set.of(Role.DIRECTOR));
        return login(director.getPhoneNumber(), "correct-password");
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
        User user = new User(UUID.randomUUID(), "Organization Integration Test User", phoneNumber, roles, Instant.now(clock));
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        return userRepository.save(user);
    }
}
