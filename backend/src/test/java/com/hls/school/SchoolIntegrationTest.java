package com.hls.school;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hls.identity.internal.Role;
import com.hls.identity.internal.User;
import com.hls.identity.internal.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
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
 * Testcontainers-backed end-to-end coverage: real Postgres (V1-V4 Flyway
 * migrations applied), a real login through Identity's
 * {@code /api/v1/auth/login} to obtain a bearer token, then real HTTP calls
 * against {@code ZoneController}. Covers tasks.md T012/T018/T024 (User
 * Stories 1-3) and quickstart.md's scenarios.
 *
 * <p><b>Environment note</b>: requires a running Docker daemon.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers
class SchoolIntegrationTest {

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

    // ---- User Story 1 (T012): create + get a Zone, 404 for unknown id --------------------

    @Test
    void createZone_thenGetById_returnsItWithExactName() throws Exception {
        String accessToken = loginAsDirector();

        String body = mockMvc.perform(post("/api/v1/school/zones")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new ZoneRequestBody("North Chennai"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("North Chennai"))
                .andReturn().getResponse().getContentAsString();
        UUID zoneId = UUID.fromString(objectMapper.readTree(body).get("id").asText());

        mockMvc.perform(get("/api/v1/school/zones/" + zoneId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("North Chennai"));
    }

    @Test
    void getZone_forUnknownId_returnsNotFound() throws Exception {
        String accessToken = loginAsDirector();

        mockMvc.perform(get("/api/v1/school/zones/" + UUID.randomUUID())
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNotFound());
    }

    // ---- User Story 2 (T018): assign, reassign, and a conflicting reassignment -----------

    @Test
    void assignSchoolToZone_thenReassign_currentZoneReflectsTheReassignment() throws Exception {
        String accessToken = loginAsDirector();
        UUID zoneA = createZone(accessToken, "Zone A");
        UUID zoneB = createZone(accessToken, "Zone B");
        UUID schoolId = UUID.randomUUID();

        String firstBody = mockMvc.perform(post("/api/v1/school/school-zone-assignments")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new AssignRequestBody(schoolId, zoneA, null))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        mockMvc.perform(get("/api/v1/school/schools/" + schoolId + "/zone")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("CURRENT_ZONE"))
                .andExpect(jsonPath("$.zoneId").value(zoneA.toString()));

        // Assigning again with no endsAssignmentId, while the School already has a
        // current Zone, is the same "conflict, not silently proceed" rule
        // AccountabilityService.assignSchoolManager already established — this proves
        // it's a clean 409 (ZoneAssignmentConflictException), not a raw DB constraint
        // violation surfacing as a 500.
        mockMvc.perform(post("/api/v1/school/school-zone-assignments")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new AssignRequestBody(schoolId, zoneB, null))))
                .andExpect(status().isConflict());

        // The real reassignment path: name the current assignment's row id explicitly.
        UUID currentAssignmentId = UUID.fromString(objectMapper.readTree(firstBody).get("id").asText());
        mockMvc.perform(post("/api/v1/school/school-zone-assignments")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new AssignRequestBody(schoolId, zoneB, currentAssignmentId))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/school/schools/" + schoolId + "/zone")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.zoneId").value(zoneB.toString()));
    }

    @Test
    void concurrentReassignment_namingTheSameAssignment_exactlyOneSucceeds() throws Exception {
        String accessToken = loginAsDirector();
        UUID zoneA = createZone(accessToken, "Zone A2");
        UUID zoneB = createZone(accessToken, "Zone B2");
        UUID zoneC = createZone(accessToken, "Zone C2");
        UUID schoolId = UUID.randomUUID();
        String firstBody = mockMvc.perform(post("/api/v1/school/school-zone-assignments")
                        .header("Authorization", "Bearer " + accessToken).contentType("application/json")
                        .content(objectMapper.writeValueAsString(new AssignRequestBody(schoolId, zoneA, null))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        UUID currentAssignmentId = UUID.fromString(objectMapper.readTree(firstBody).get("id").asText());

        int firstStatus = mockMvc.perform(post("/api/v1/school/school-zone-assignments")
                        .header("Authorization", "Bearer " + accessToken).contentType("application/json")
                        .content(objectMapper.writeValueAsString(new AssignRequestBody(schoolId, zoneB, currentAssignmentId))))
                .andReturn().getResponse().getStatus();
        int secondStatus = mockMvc.perform(post("/api/v1/school/school-zone-assignments")
                        .header("Authorization", "Bearer " + accessToken).contentType("application/json")
                        .content(objectMapper.writeValueAsString(new AssignRequestBody(schoolId, zoneC, currentAssignmentId))))
                .andReturn().getResponse().getStatus();

        org.assertj.core.api.Assertions.assertThat(List.of(firstStatus, secondStatus))
                .containsExactlyInAnyOrder(200, 409);
    }

    // ---- User Story 3 (T024): a Zone's Schools; an unassigned School reports UNASSIGNED --

    @Test
    void zoneSchools_returnsExactlyTheSchoolsCurrentlyAssigned() throws Exception {
        String accessToken = loginAsDirector();
        UUID zoneId = createZone(accessToken, "Zone With Schools");
        UUID schoolA = UUID.randomUUID();
        UUID schoolB = UUID.randomUUID();
        mockMvc.perform(post("/api/v1/school/school-zone-assignments")
                        .header("Authorization", "Bearer " + accessToken).contentType("application/json")
                        .content(objectMapper.writeValueAsString(new AssignRequestBody(schoolA, zoneId, null))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/school/school-zone-assignments")
                        .header("Authorization", "Bearer " + accessToken).contentType("application/json")
                        .content(objectMapper.writeValueAsString(new AssignRequestBody(schoolB, zoneId, null))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/school/zones/" + zoneId + "/schools")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void schoolZone_forNeverAssignedSchool_reportsUnassigned() throws Exception {
        String accessToken = loginAsDirector();

        mockMvc.perform(get("/api/v1/school/schools/" + UUID.randomUUID() + "/zone")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("UNASSIGNED"));
    }

    // ---- authorization: non-Director/Admin callers are rejected ---------------------------

    @Test
    void nonDirectorOrAdminCaller_isDeniedByZoneEndpoints() throws Exception {
        User teacher = seedUser("+919820000097", "irrelevant-password", Set.of(Role.TEACHER));
        String accessToken = login(teacher.getPhoneNumber(), "irrelevant-password");

        mockMvc.perform(post("/api/v1/school/zones")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new ZoneRequestBody("Should Be Denied"))))
                .andExpect(status().isForbidden());
    }

    // ---- specs/008-school-places: User Story 1 (T009) — add a place --------------------

    @Test
    void addPlace_asDirectorOrAdmin_succeeds_asOtherRole_isDenied() throws Exception {
        String accessToken = loginAsDirector();
        UUID zoneId = createZone(accessToken, "Places Zone A");

        mockMvc.perform(post("/api/v1/school/places")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new PlaceRequestBody(zoneId, "Ambattur", "600053"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.zoneId").value(zoneId.toString()))
                .andExpect(jsonPath("$.name").value("Ambattur"))
                .andExpect(jsonPath("$.pincode").value("600053"));

        User teacher = seedUser("+919820000098", "irrelevant-password", Set.of(Role.TEACHER));
        String teacherToken = login(teacher.getPhoneNumber(), "irrelevant-password");
        mockMvc.perform(post("/api/v1/school/places")
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new PlaceRequestBody(zoneId, "Should Be Denied", "600000"))))
                .andExpect(status().isForbidden());
    }

    // ---- specs/008-school-places: User Story 2 (T014) — lookup by pincode/name ---------

    @Test
    void findPlaces_byPincodeSpanningTwoZones_returnsBothPlaces_andUnknownPincodeReturnsEmptyArray() throws Exception {
        String accessToken = loginAsDirector();
        UUID zoneA = createZone(accessToken, "Places Zone B");
        UUID zoneB = createZone(accessToken, "Places Zone C");
        addPlace(accessToken, zoneA, "Chettipalayam", "641045");
        addPlace(accessToken, zoneB, "Neighboring Village", "641045");

        mockMvc.perform(get("/api/v1/school/places").param("pincode", "641045")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        mockMvc.perform(get("/api/v1/school/places").param("pincode", "010101")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(get("/api/v1/school/places").param("name", "chettipalayam")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].zoneId").value(zoneA.toString()));
    }

    // ---- specs/008-school-places: User Story 3 (T019) — a Zone's places ----------------

    @Test
    void getZonePlaces_returnsExactlyThreeAddedPlaces_andEmptyZoneReturnsEmptyList() throws Exception {
        String accessToken = loginAsDirector();
        UUID zoneWithPlaces = createZone(accessToken, "Places Zone D");
        UUID emptyZone = createZone(accessToken, "Places Zone E");
        addPlace(accessToken, zoneWithPlaces, "Place One", "600001");
        addPlace(accessToken, zoneWithPlaces, "Place Two", "600002");
        addPlace(accessToken, zoneWithPlaces, "Place Three", "600003");

        mockMvc.perform(get("/api/v1/school/zones/" + zoneWithPlaces + "/places")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));

        mockMvc.perform(get("/api/v1/school/zones/" + emptyZone + "/places")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    // ---- helpers -------------------------------------------------------------------------

    private record ZoneRequestBody(String name) {
    }

    private record AssignRequestBody(UUID schoolId, UUID zoneId, UUID endsAssignmentId) {
    }

    private record PlaceRequestBody(UUID zoneId, String name, String pincode) {
    }

    private void addPlace(String accessToken, UUID zoneId, String name, String pincode) throws Exception {
        mockMvc.perform(post("/api/v1/school/places")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new PlaceRequestBody(zoneId, name, pincode))))
                .andExpect(status().isOk());
    }

    private UUID createZone(String accessToken, String name) throws Exception {
        String body = mockMvc.perform(post("/api/v1/school/zones")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new ZoneRequestBody(name))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(body).get("id").asText());
    }

    private static final AtomicInteger DIRECTOR_SEQUENCE = new AtomicInteger(1);

    private String loginAsDirector() throws Exception {
        String phoneNumber = "+9198320000" + String.format("%02d", DIRECTOR_SEQUENCE.getAndIncrement());
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
        User user = new User(UUID.randomUUID(), "School Integration Test User", phoneNumber, roles, Instant.now(clock));
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        return userRepository.save(user);
    }
}
