package com.hls.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hls.audit.api.AuditWriter;
import com.hls.audit.api.dto.AuditAction;
import com.hls.audit.api.dto.AuditRecordRequest;
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
 * Testcontainers-backed end-to-end coverage: real Postgres (V1-V3 Flyway
 * migrations applied), a real login through Identity's
 * {@code /api/v1/auth/login} to obtain a bearer token, then real HTTP calls
 * against {@code AuditController}. No real caller module exists yet
 * (research.md §1), so the write path is exercised by calling
 * {@link AuditWriter} directly, in process, the same way a future
 * attendance/payroll/expense/substitution module's service code will.
 * Covers tasks.md T009/T013/T019/T020 (User Stories 1-3) and quickstart.md's
 * scenarios.
 *
 * <p><b>Environment note</b>: requires a running Docker daemon.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers
class AuditIntegrationTest {

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
    @Autowired
    private AuditWriter auditWriter;

    // ---- User Story 1 (T009): direct AuditWriter calls for each illustrative record type --

    @Test
    void record_oncePerIllustrativeRecordType_persistsWithCorrectFields() throws Exception {
        UUID actorUserId = UUID.randomUUID();

        UUID attendanceEntryId = auditWriter.record(new AuditRecordRequest(
                "attendance", "AttendanceRecord", "attendance-record-1", AuditAction.UPDATED,
                "status: Present -> Leave", "{\"status\":\"Present\"}", "{\"status\":\"Leave\"}",
                actorUserId, "MANAGER", "req-1"));
        UUID payrollEntryId = auditWriter.record(new AuditRecordRequest(
                "payroll", "Payslip", "payslip-1", AuditAction.CORRECTED,
                "netSalary: 14400 -> 14450 (rounding fix)", "{\"netSalary\":14400}", "{\"netSalary\":14450}",
                actorUserId, "ADMIN", "req-2"));
        UUID schoolPaymentEntryId = auditWriter.record(new AuditRecordRequest(
                "schoolbilling", "SchoolPayment", "payment-1", AuditAction.CREATED,
                "payment recorded", null, "{\"amount\":50000}", actorUserId, "MANAGER", "req-3"));
        UUID expenseEntryId = auditWriter.record(new AuditRecordRequest(
                "expense", "Expense", "expense-1", AuditAction.UPDATED,
                "status: Pending -> Approved", "{\"status\":\"Pending\"}", "{\"status\":\"Approved\"}",
                actorUserId, "ADMIN", "req-4"));
        UUID substitutionEntryId = auditWriter.record(new AuditRecordRequest(
                "substitution", "Substitution", "substitution-1", AuditAction.CREATED,
                "substitute assigned", null, "{\"substituteId\":\"teacher-2\"}", actorUserId, "MANAGER", "req-5"));

        String accessToken = loginAsDirector();
        assertHistoryContainsIdWithFields(accessToken, "AttendanceRecord", "attendance-record-1", attendanceEntryId, actorUserId);
        assertHistoryContainsIdWithFields(accessToken, "Payslip", "payslip-1", payrollEntryId, actorUserId);
        assertHistoryContainsIdWithFields(accessToken, "SchoolPayment", "payment-1", schoolPaymentEntryId, actorUserId);
        assertHistoryContainsIdWithFields(accessToken, "Expense", "expense-1", expenseEntryId, actorUserId);
        assertHistoryContainsIdWithFields(accessToken, "Substitution", "substitution-1", substitutionEntryId, actorUserId);
    }

    private void assertHistoryContainsIdWithFields(
            String accessToken, String entityType, String entityId, UUID expectedEntryId, UUID expectedActor) throws Exception {
        mockMvc.perform(get("/api/v1/audit/" + entityType + "/" + entityId + "/history")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(expectedEntryId.toString()))
                .andExpect(jsonPath("$[0].actorUserId").value(expectedActor.toString()));
    }

    // ---- User Story 2 (T013): viewing over real HTTP, Director/Admin vs. Manager ---------

    @Test
    void history_forDirectorOrAdmin_returnsEveryEntryInOrder() throws Exception {
        UUID actorUserId = UUID.randomUUID();
        UUID createdId = auditWriter.record(new AuditRecordRequest(
                "attendance", "AttendanceRecord", "attendance-record-2", AuditAction.CREATED,
                "created", null, "{\"status\":\"Present\"}", actorUserId, "MANAGER", null));
        UUID updatedId = auditWriter.record(new AuditRecordRequest(
                "attendance", "AttendanceRecord", "attendance-record-2", AuditAction.UPDATED,
                "status: Present -> Leave", "{\"status\":\"Present\"}", "{\"status\":\"Leave\"}",
                actorUserId, "MANAGER", null));

        String accessToken = loginAsDirector();
        mockMvc.perform(get("/api/v1/audit/AttendanceRecord/attendance-record-2/history")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(createdId.toString()))
                .andExpect(jsonPath("$[1].id").value(updatedId.toString()));
    }

    @Test
    void history_forSingleCreationEntry_returnsThatOneEntry() throws Exception {
        UUID entryId = auditWriter.record(new AuditRecordRequest(
                "expense", "Expense", "expense-2", AuditAction.CREATED,
                "expense claim submitted", null, "{\"amount\":250}", UUID.randomUUID(), "MANAGER", null));

        String accessToken = loginAsDirector();
        mockMvc.perform(get("/api/v1/audit/Expense/expense-2/history")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(entryId.toString()));
    }

    @Test
    void history_forManager_isForbidden() throws Exception {
        User manager = seedUser("+919820000098", "irrelevant-password", Set.of(Role.MANAGER));
        String accessToken = login(manager.getPhoneNumber(), "irrelevant-password");

        mockMvc.perform(get("/api/v1/audit/AttendanceRecord/attendance-record-2/history")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isForbidden());
    }

    // ---- User Story 3 (T019): no write surface exists at all -----------------------------

    @Test
    void writeVerbsAgainstTheHistoryPath_areNotFoundOrNotAllowed_notForbidden() throws Exception {
        String accessToken = loginAsDirector();

        mockMvc.perform(put("/api/v1/audit/AttendanceRecord/attendance-record-1/history")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isIn(404, 405));
        mockMvc.perform(patch("/api/v1/audit/AttendanceRecord/attendance-record-1/history")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isIn(404, 405));
        mockMvc.perform(delete("/api/v1/audit/AttendanceRecord/attendance-record-1/history")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isIn(404, 405));
    }

    // ---- User Story 3 (T020): a correction appends, it never overwrites ------------------

    @Test
    void correction_appendsANewEntry_originalRemainsUnchanged() throws Exception {
        UUID actorUserId = UUID.randomUUID();
        UUID createdId = auditWriter.record(new AuditRecordRequest(
                "expense", "Expense", "expense-3", AuditAction.CREATED,
                "expense claim submitted", null, "{\"amount\":500}", actorUserId, "MANAGER", null));
        UUID correctedId = auditWriter.record(new AuditRecordRequest(
                "expense", "Expense", "expense-3", AuditAction.CORRECTED,
                "amount: 500 -> 5000 (typo fix)", "{\"amount\":500}", "{\"amount\":5000}",
                actorUserId, "ADMIN", null));

        String accessToken = loginAsDirector();
        mockMvc.perform(get("/api/v1/audit/Expense/expense-3/history")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(createdId.toString()))
                .andExpect(jsonPath("$[0].action").value("CREATED"))
                .andExpect(jsonPath("$[0].afterValue").value("{\"amount\":500}"))
                .andExpect(jsonPath("$[1].id").value(correctedId.toString()))
                .andExpect(jsonPath("$[1].action").value("CORRECTED"))
                .andExpect(jsonPath("$[1].afterValue").value("{\"amount\":5000}"));
    }

    // ---- helpers -------------------------------------------------------------------------

    private static final AtomicInteger DIRECTOR_SEQUENCE = new AtomicInteger(1);

    private String loginAsDirector() throws Exception {
        String phoneNumber = "+9198310000" + String.format("%02d", DIRECTOR_SEQUENCE.getAndIncrement());
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
        User user = new User(UUID.randomUUID(), "Audit Integration Test User", phoneNumber, roles, Instant.now(clock));
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        return userRepository.save(user);
    }
}
