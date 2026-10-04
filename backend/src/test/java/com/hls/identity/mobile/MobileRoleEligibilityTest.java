package com.hls.identity.mobile;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.auth.AuthDtos;
import com.hls.identity.otp.OtpChannel;
import com.hls.identity.otp.OtpDtos;
import com.hls.identity.otp.OtpPurpose;
import com.hls.identity.user.Role;
import com.hls.support.MobileTestBase;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Spec 018 T023 (US1, FR-003): only Teacher, Manager and Director accounts get an app session.
 * Admin-only and System-only accounts get 403 WEB_ONLY_ROLE after valid credentials, with no session,
 * and the refusal never reveals anything for a wrong password.
 */
class MobileRoleEligibilityTest extends MobileTestBase {

    private record Account(String phone, UUID id) {}

    private Account account(Role... roles) {
        String phone = nextPhone();
        UUID id = userAdminService.createUser("Eligibility " + phone, phone, Set.of(roles), null, PASSWORD).getId();
        return new Account(phone, id);
    }

    private Raw androidLogin(Account account, String password) {
        return postRaw("/api/v1/auth/login", new AuthDtos.LoginRequest(account.phone(), password), ANDROID);
    }

    @Test
    void adminOnlyAndSystemOnlyAreRefusedWithNoSession() {
        for (Role role : new Role[] {Role.ADMIN, Role.SYSTEM}) {
            Account account = account(role);

            Raw response = androidLogin(account, PASSWORD);

            assertThat(response.status()).as(role.name()).isEqualTo(403);
            assertThat(response.string("code")).isEqualTo("WEB_ONLY_ROLE");
            assertThat(response.string("message")).isEqualTo("Your account uses the HLS web application.");
            assertThat(response.map()).doesNotContainKeys("accessToken", "renewalCredential");
            assertThat(response.setCookies()).isEmpty();
            assertThat(activeSessionCount(account.id())).as(role.name()).isZero();
        }
    }

    @Test
    void theRefusalIsRecordedAsASignInFailure() {
        Account account = account(Role.ADMIN);

        androidLogin(account, PASSWORD);

        awaitTrue(
                "the refusal in login history",
                () -> loginHistoryCount(account.id(), "SIGN_IN_FAILURE", "Role not permitted in the mobile app") == 1);
    }

    @Test
    void anAppRoleAloneOrCombinedWithAdminIsAllowed() {
        for (Role[] roles : new Role[][] {
            {Role.TEACHER}, {Role.MANAGER}, {Role.DIRECTOR}, {Role.ADMIN, Role.TEACHER}, {Role.SYSTEM, Role.MANAGER}
        }) {
            Account account = account(roles);

            Raw response = androidLogin(account, PASSWORD);

            assertThat(response.status()).as(Set.of(roles).toString()).isEqualTo(200);
            assertThat(response.string("renewalCredential")).isNotBlank();
            assertThat(activeSessionCount(account.id())).isEqualTo(1);
        }
    }

    @Test
    void aWrongPasswordForAWebOnlyAccountIsStillTheGeneric401() {
        Account account = account(Role.ADMIN);

        Raw response = androidLogin(account, "wrong-password-1");

        assertThat(response.status()).isEqualTo(401);
        assertThat(response.map()).doesNotContainKey("code");
        assertThat(response.string("message")).isEqualTo("Your phone number/username or password is incorrect.");
    }

    @Test
    void webSignInForAnAdminIsUnaffected() {
        Account account = account(Role.ADMIN);

        Raw response = postRaw("/api/v1/auth/login", new AuthDtos.LoginRequest(account.phone(), PASSWORD), null);

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.hasRenewalCookie()).isTrue();
    }

    @Test
    void otpSignInIsRefusedTheSameWayForAWebOnlyAccount() {
        Account account = account(Role.SYSTEM);
        postRaw("/api/v1/auth/otp/request", new OtpDtos.OtpRequest(account.phone(), OtpChannel.SMS, OtpPurpose.SIGN_IN), ANDROID);
        String code = latestSmsCode();

        Raw verified = postRaw(
                "/api/v1/auth/otp/verify",
                new OtpDtos.OtpVerifyRequest(account.phone(), code, OtpPurpose.SIGN_IN, null),
                ANDROID);

        assertThat(verified.status()).isEqualTo(403);
        assertThat(verified.string("code")).isEqualTo("WEB_ONLY_ROLE");
        assertThat(activeSessionCount(account.id())).isZero();
    }
}
