package com.hls.identity.user;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.auth.PasswordPolicy;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.core.JdbcTemplate;

/** User Story 5 (FR-006): admin-triggered password reset. */
@ExtendWith(OutputCaptureExtension.class)
class AdminPasswordResetIntegrationTest extends UserManagementTestBase {

    private static final String NEW_PASSWORD = "Sup3r-Secret-Pw!";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void resetEndsSessionsKillsLiveTokensClearsLockoutAndSwitchesThePassword(CapturedOutput output) {
        String adminToken = signInAs(Role.ADMIN).token();
        Map<String, Object> user = createUserViaApi(adminToken, "Forgetful", Set.of(Role.MANAGER));
        UUID userId = UUID.fromString((String) user.get("id"));
        String phone = (String) user.get("phone");
        Signed live = signIn(userId, phone, PASSWORD);
        // lock the account with repeated failures
        for (int i = 0; i < 6; i++) {
            loginStatus(phone, "wrong-password-" + i);
        }

        Resp resp = post(
                "/api/v1/identity/users/" + userId + "/reset-password", adminToken, Map.of("newPassword", NEW_PASSWORD));

        assertThat(resp.status()).isEqualTo(204);
        assertThat(get("/api/v1/me/access-model", live.token()).status()).isEqualTo(401); // FR-006
        assertThat(loginStatus(phone, PASSWORD)).isEqualTo(401);
        assertThat(loginStatus(phone, NEW_PASSWORD)).isEqualTo(200); // lockout cleared too
        assertAuditEntry(adminToken, userId, "PASSWORD_RESET_BY_ADMIN");

        // FR-006: the password appears nowhere in logs or audit rows
        assertThat(output.getAll()).doesNotContain(NEW_PASSWORD);
        Integer leaks = jdbcTemplate.queryForObject(
                "select count(*) from user_activity_entry where detail like ?", Integer.class, "%" + NEW_PASSWORD + "%");
        assertThat(leaks).isZero();
    }

    @Test
    void aUserWithNoPasswordCanSignInWithTheNewOneAfterwards() {
        String adminToken = signInAs(Role.ADMIN).token();
        String phone = nextPhone();
        UUID userId = userAdminService
                .createUser("Otp Only", phone, Set.of(Role.TEACHER), null, null)
                .getId();

        Resp resp = post(
                "/api/v1/identity/users/" + userId + "/reset-password", adminToken, Map.of("newPassword", NEW_PASSWORD));

        assertThat(resp.status()).isEqualTo(204);
        assertThat(loginStatus(phone, NEW_PASSWORD)).isEqualTo(200);
    }

    @Test
    void passwordsViolatingPolicyAreRefusedWithSpec001sMessage() {
        String adminToken = signInAs(Role.ADMIN).token();
        Map<String, Object> user = createUserViaApi(adminToken, "Policy Target", Set.of(Role.TEACHER));
        String path = "/api/v1/identity/users/" + user.get("id") + "/reset-password";

        Resp tooShort = post(path, adminToken, Map.of("newPassword", "short"));
        assertThat(tooShort.status()).isEqualTo(400);
        assertThat(tooShort.body()).contains(PasswordPolicy.VIOLATION_MESSAGE);

        Resp equalsPhone = post(path, adminToken, Map.of("newPassword", user.get("phone")));
        assertThat(equalsPhone.status()).isEqualTo(400);
        assertThat(equalsPhone.body()).contains(PasswordPolicy.VIOLATION_MESSAGE);

        assertThat(loginStatus((String) user.get("phone"), PASSWORD)).isEqualTo(200);
    }

    @Test
    void directorManagerAndTeacherGet403() {
        UUID target = signInAs(Role.TEACHER).userId();
        for (Role role : new Role[] {Role.DIRECTOR, Role.MANAGER, Role.TEACHER}) {
            Resp resp = post(
                    "/api/v1/identity/users/" + target + "/reset-password",
                    signInAs(role).token(),
                    Map.of("newPassword", NEW_PASSWORD));
            assertThat(resp.status()).isEqualTo(403);
        }
    }
}
