package com.hls.identity;

import tools.jackson.databind.ObjectMapper;
import com.hls.identity.api.ManagerScopeQueries;
import com.hls.identity.api.TeacherScopeQueries;
import com.hls.identity.internal.*;
import com.hls.organization.api.AccountabilityCommands;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Testcontainers-backed end-to-end coverage: real Postgres, real Flyway
 * migration (V1), real Spring Security JWT filter chain. Exercises
 * quickstart.md's scenarios 1–6 at the HTTP layer where a business endpoint
 * exists (login/OTP/refresh/sessions), and via direct bean calls for the two
 * scope guards, which have no HTTP endpoint of their own — other modules call
 * them in-process.
 *
 * <p><b>Environment note</b>: requires a running Docker daemon. Not runnable in
 * every sandbox — see the implementation report for details.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers
class IdentityIntegrationTest {

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
    private ManagerScopeQueries managerScopeQueries;
    @Autowired
    private TeacherScopeQueries teacherScopeQueries;
    @Autowired
    private AccountabilityCommands accountabilityCommands;
    @Autowired
    private OtpChallengeRepository otpChallengeRepository;
    @Autowired
    private Clock clock;

    private static final Pattern REFRESH_COOKIE_PATTERN = Pattern.compile("refresh_token=([^;]+)");

    // ---- Scenario 1 (quickstart.md): password login, then session list/revoke, then refresh denied ----

    @Test
    void passwordLogin_thenListAndRevokeSession_thenRefreshIsDenied() throws Exception {
        User user = seedUser("+919810000001", "correct-password", Set.of(Role.MANAGER));

        var loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new java.util.HashMap<>() {{
                            put("phoneNumber", user.getPhoneNumber());
                            put("password", "correct-password");
                        }})))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andReturn();

        String accessToken = objectMapper.readTree(loginResult.getResponse().getContentAsString()).get("accessToken").asText();
        String refreshCookie = extractRefreshToken(loginResult.getResponse().getHeader("Set-Cookie"));

        // List own sessions — exactly one, not revoked.
        mockMvc.perform(get("/api/v1/auth/sessions").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        UUID sessionId = UUID.fromString(objectMapper.readTree(
                mockMvc.perform(get("/api/v1/auth/sessions").header("Authorization", "Bearer " + accessToken))
                        .andReturn().getResponse().getContentAsString()).get(0).get("id").asText());

        // Revoke it, then refreshing with the now-revoked cookie must be denied (SC-005).
        mockMvc.perform(delete("/api/v1/auth/sessions/" + sessionId).header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/auth/refresh").cookie(new jakarta.servlet.http.Cookie("refresh_token", refreshCookie)))
                .andExpect(status().isUnauthorized());
    }

    // ---- Scenario 2: OTP login end to end -------------------------------------------------

    @Test
    void otpLogin_withCorrectCode_succeeds() throws Exception {
        String phoneNumber = "+919810000002";
        seedUser(phoneNumber, null, Set.of(Role.TEACHER));

        // FR-015 means the real /otp/request → /otp/verify flow never exposes the
        // generated code anywhere observable from outside the process (the dev
        // OtpSender only logs it). So this test seeds its own deterministic
        // challenge directly, exactly mirroring what AuthenticationService.requestOtp
        // would have produced, rather than trying to scrape a log line.
        String rawCode = "482913";
        otpChallengeRepository.save(new OtpChallenge(
                UUID.randomUUID(), phoneNumber, passwordEncoder.encode(rawCode), Instant.now(clock).plusSeconds(300)));

        mockMvc.perform(post("/api/v1/auth/otp/verify")
                        .contentType("application/json")
                        .content("{\"phoneNumber\":\"" + phoneNumber + "\",\"code\":\"" + rawCode + "\",\"channel\":\"MOBILE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists());
    }

    // ---- Scenario 3/4: Manager scoping, live + fail-closed -----------------------------

    @Test
    void managerScopeGuard_allowsAssignedManagerAndDeniesUnassignedOne() {
        UUID managerA = UUID.randomUUID();
        UUID managerB = UUID.randomUUID();
        UUID schoolId = UUID.randomUUID();
        accountabilityCommands.assignSchoolManager(schoolId, managerA, null, UUID.randomUUID());

        assertThat(managerScopeQueries.isAllowedForSchool(managerA, schoolId)).isTrue();
        assertThat(managerScopeQueries.isAllowedForSchool(managerB, schoolId)).isFalse();
    }

    // ---- User Story 6: Teacher self-scoping --------------------------------------------

    @Test
    void teacherScopeGuard_allowsOwnRecordAndDeniesOthers() {
        UUID ownTeacherId = UUID.randomUUID();
        UUID otherTeacherId = UUID.randomUUID();
        User teacher = seedUser("+919810000003", "irrelevant", Set.of(Role.TEACHER));
        teacher.setLinkedTeacherId(ownTeacherId);
        userRepository.save(teacher);

        assertThat(teacherScopeQueries.isAllowed(teacher.getId(), ownTeacherId)).isTrue();
        assertThat(teacherScopeQueries.isAllowed(teacher.getId(), otherTeacherId)).isFalse();
    }

    // ---- helpers -------------------------------------------------------------------------

    private User seedUser(String phoneNumber, String rawPasswordOrNull, Set<Role> roles) {
        User user = new User(UUID.randomUUID(), "Integration Test User", phoneNumber, roles, Instant.now(clock));
        if (rawPasswordOrNull != null) {
            user.setPasswordHash(passwordEncoder.encode(rawPasswordOrNull));
        }
        return userRepository.save(user);
    }

    private String extractRefreshToken(String setCookieHeader) {
        Matcher matcher = REFRESH_COOKIE_PATTERN.matcher(setCookieHeader);
        if (!matcher.find()) {
            throw new AssertionError("No refresh_token cookie in Set-Cookie header: " + setCookieHeader);
        }
        return matcher.group(1);
    }

}
