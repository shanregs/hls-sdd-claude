package com.hls.organization;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.hls.identity.internal.Role;
import com.hls.identity.internal.User;
import com.hls.identity.internal.UserRepository;
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
 * Testcontainers-backed end-to-end coverage: real Postgres (all Flyway
 * migrations through {@code V8} applied), a real login through Identity's
 * {@code /api/v1/auth/login} to obtain a bearer token, real HTTP calls
 * against {@code OrganizationController}, and — since specs/006-zone-scoping's
 * rework — real Zone/School-Zone setup through {@code school}'s own,
 * already-shipped endpoints (never a stand-in). Covers specs/003's original
 * User Stories 1-3 (now Zone-constrained) and specs/006-zone-scoping's
 * reworked User Stories 1-3.
 *
 * <p>Imports {@code identity.internal.*} directly for user-seeding, exactly as
 * {@code IdentityIntegrationTest} does within its own module — {@code ArchitectureTest}
 * excludes test sources from its boundary check.
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

    // ---- specs/003 User Story 1: assign over real HTTP, then query ----------------------

    @Test
    void assignSchoolManager_overHttp_thenAccountableManagerQueryReturnsIt() throws Exception {
        String accessToken = loginAsDirector();
        UUID managerId = UUID.randomUUID();
        UUID zoneId = createZone(accessToken, "Org Zone A");
        UUID schoolId = assignSchoolToZone(accessToken, UUID.randomUUID(), zoneId);
        assignManagerToZone(accessToken, zoneId, managerId);

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

    // ---- specs/003 User Story 2: conflicting reassignment, and FR-013 independence ------

    @Test
    void reassignment_namingAnAlreadyEndedAssignment_isRejectedAsConflict() throws Exception {
        String accessToken = loginAsDirector();
        UUID managerA = UUID.randomUUID();
        UUID managerB = UUID.randomUUID();
        UUID managerC = UUID.randomUUID();
        UUID zoneId = createZone(accessToken, "Org Zone B");
        UUID schoolId = assignSchoolToZone(accessToken, UUID.randomUUID(), zoneId);
        assignManagerToZone(accessToken, zoneId, managerA);
        assignManagerToZone(accessToken, zoneId, managerB);
        assignManagerToZone(accessToken, zoneId, managerC);

        String firstAssignBody = mockMvc.perform(post("/api/v1/organization/school-assignments")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new AssignmentRequestBody(schoolId, null, managerA, null))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        UUID currentAssignmentId = UUID.fromString(objectMapper.readTree(firstAssignBody).get("id").asText());

        // The first reassignment naming this row succeeds...
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

    // FR-013.
    @Test
    void reassigningSchoolManager_leavesAnUnrelatedTeachersAccountableManagerUnchanged() throws Exception {
        String accessToken = loginAsDirector();
        UUID teacherId = UUID.randomUUID();
        UUID teachersManager = UUID.randomUUID();
        UUID schoolManagerA = UUID.randomUUID();
        UUID schoolManagerB = UUID.randomUUID();
        UUID zoneId = createZone(accessToken, "Org Zone C");
        UUID schoolId = assignSchoolToZone(accessToken, UUID.randomUUID(), zoneId);
        assignManagerToZone(accessToken, zoneId, schoolManagerA);
        assignManagerToZone(accessToken, zoneId, schoolManagerB);

        // Assign the Teacher to its own Manager — no Zone setup involved at all.
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

    // ---- specs/003 User Story 3: end-without-replacement appears on unassigned list -----

    @Test
    void endingAssignmentWithoutReplacement_appearsOnUnassignedListInTheSameSession() throws Exception {
        String accessToken = loginAsDirector();
        UUID managerId = UUID.randomUUID();
        UUID zoneId = createZone(accessToken, "Org Zone D");
        UUID schoolId = assignSchoolToZone(accessToken, UUID.randomUUID(), zoneId);
        assignManagerToZone(accessToken, zoneId, managerId);

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

    // ---- specs/006 User Story 1: assign/remove Manager coverage of a Zone ---------------

    @Test
    void assignManagerToZone_thenRemove_reflectsInCoverage() throws Exception {
        String accessToken = loginAsDirector();
        UUID zoneId = createZone(accessToken, "Org Zone E");
        UUID managerId = UUID.randomUUID();

        String body = mockMvc.perform(post("/api/v1/organization/zone-manager-assignments")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new ZoneManagerRequestBody(zoneId, managerId))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        UUID assignmentId = UUID.fromString(objectMapper.readTree(body).get("id").asText());

        mockMvc.perform(get("/api/v1/organization/zones/" + zoneId + "/coverage")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.managerIds[0]").value(managerId.toString()));

        mockMvc.perform(delete("/api/v1/organization/zone-manager-assignments/" + assignmentId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/organization/zones/" + zoneId + "/coverage")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.managerIds.length()").value(0));
    }

    @Test
    void assignManagerToZone_unknownZoneId_returns404() throws Exception {
        String accessToken = loginAsDirector();

        mockMvc.perform(post("/api/v1/organization/zone-manager-assignments")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new ZoneManagerRequestBody(UUID.randomUUID(), UUID.randomUUID()))))
                .andExpect(status().isNotFound());
    }

    // ---- specs/006 User Story 2: assignSchoolManager is Zone-constrained ----------------

    @Test
    void assignSchoolManager_managerCoveringSchoolsZone_succeeds() throws Exception {
        String accessToken = loginAsDirector();
        UUID managerId = UUID.randomUUID();
        UUID zoneId = createZone(accessToken, "Org Zone F");
        UUID schoolId = assignSchoolToZone(accessToken, UUID.randomUUID(), zoneId);
        assignManagerToZone(accessToken, zoneId, managerId);

        mockMvc.perform(post("/api/v1/organization/school-assignments")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new AssignmentRequestBody(schoolId, null, managerId, null))))
                .andExpect(status().isOk());
    }

    @Test
    void assignSchoolManager_managerNotCoveringSchoolsZone_returns422() throws Exception {
        String accessToken = loginAsDirector();
        UUID zoneId = createZone(accessToken, "Org Zone G");
        UUID schoolId = assignSchoolToZone(accessToken, UUID.randomUUID(), zoneId);
        UUID managerNotCovering = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/organization/school-assignments")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new AssignmentRequestBody(schoolId, null, managerNotCovering, null))))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void assignSchoolManager_schoolWithNoZone_returns422() throws Exception {
        String accessToken = loginAsDirector();

        mockMvc.perform(post("/api/v1/organization/school-assignments")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new AssignmentRequestBody(UUID.randomUUID(), null, UUID.randomUUID(), null))))
                .andExpect(status().isUnprocessableEntity());
    }

    // quickstart.md Scenario 4 / SC-004.
    @Test
    void assignTeacherManager_stillWorksWithNoZoneSetupAtAll() throws Exception {
        String accessToken = loginAsDirector();

        mockMvc.perform(post("/api/v1/organization/teacher-assignments")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new AssignmentRequestBody(null, UUID.randomUUID(), UUID.randomUUID(), null))))
                .andExpect(status().isOk());
    }

    // ---- specs/006 User Story 3: zone coverage lookup ------------------------------------

    @Test
    void getZoneCoverage_returnsManagersAndSchoolsInOneLookup() throws Exception {
        String accessToken = loginAsDirector();
        UUID zoneId = createZone(accessToken, "Org Zone H");
        UUID managerA = UUID.randomUUID();
        UUID managerB = UUID.randomUUID();
        assignManagerToZone(accessToken, zoneId, managerA);
        assignManagerToZone(accessToken, zoneId, managerB);
        UUID schoolId = assignSchoolToZone(accessToken, UUID.randomUUID(), zoneId);

        mockMvc.perform(get("/api/v1/organization/zones/" + zoneId + "/coverage")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.managerIds.length()").value(2))
                .andExpect(jsonPath("$.schoolIds[0]").value(schoolId.toString()));
    }

    // ---- helpers -------------------------------------------------------------------------

    private record AssignmentRequestBody(UUID schoolId, UUID teacherId, UUID managerId, UUID endsAssignmentId) {
    }

    private record ZoneManagerRequestBody(UUID zoneId, UUID managerId) {
    }

    private record ZoneRequestBody(String name) {
    }

    private record SchoolZoneRequestBody(UUID schoolId, UUID zoneId) {
    }

    /** Creates a real Zone through `school`'s own endpoint (specs/007-school-zone) — never a stand-in. */
    private UUID createZone(String accessToken, String name) throws Exception {
        String body = mockMvc.perform(post("/api/v1/school/zones")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new ZoneRequestBody(name))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(body).get("id").asText());
    }

    /** Assigns {@code schoolId} to {@code zoneId} through `school`'s own endpoint; returns {@code schoolId} for chaining. */
    private UUID assignSchoolToZone(String accessToken, UUID schoolId, UUID zoneId) throws Exception {
        mockMvc.perform(post("/api/v1/school/school-zone-assignments")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new SchoolZoneRequestBody(schoolId, zoneId))))
                .andExpect(status().isOk());
        return schoolId;
    }

    private void assignManagerToZone(String accessToken, UUID zoneId, UUID managerId) throws Exception {
        mockMvc.perform(post("/api/v1/organization/zone-manager-assignments")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new ZoneManagerRequestBody(zoneId, managerId))))
                .andExpect(status().isOk());
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
