package com.hls.identity.mobile;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.otp.OtpChannel;
import com.hls.identity.otp.OtpDtos;
import com.hls.identity.otp.OtpPurpose;
import com.hls.identity.user.Role;
import com.hls.support.MobileTestBase;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Spec 018 T022 (US1): OTP sign-in for the Android client returns the renewal credential in the
 * body and no cookie; password-reset verification is unchanged.
 */
class MobileOtpVerifyTest extends MobileTestBase {

    private String requestCodeFor(String phone, OtpPurpose purpose, String clientHeader) {
        Raw requested = postRaw(
                "/api/v1/auth/otp/request", new OtpDtos.OtpRequest(phone, OtpChannel.SMS, purpose), clientHeader);
        assertThat(requested.status()).isEqualTo(200);
        return latestSmsCode();
    }

    @Test
    void androidOtpSignInReturnsTheCredentialInTheBodyAndNoCookie() {
        String phone = nextPhone();
        userAdminService.createUser("OTP Teacher", phone, Set.of(Role.TEACHER), null, null);
        String code = requestCodeFor(phone, OtpPurpose.SIGN_IN, ANDROID);

        Raw verified = postRaw(
                "/api/v1/auth/otp/verify", new OtpDtos.OtpVerifyRequest(phone, code, OtpPurpose.SIGN_IN, null), ANDROID);

        assertThat(verified.status()).isEqualTo(200);
        assertThat(verified.hasRenewalCookie()).isFalse();
        assertThat(verified.string("accessToken")).isNotBlank();
        assertThat(verified.string("renewalCredential")).isNotBlank();
        assertThat(verified.body()).contains("TEACHER");
    }

    @Test
    void webOtpSignInIsUnchanged() {
        String phone = nextPhone();
        userAdminService.createUser("OTP Teacher Web", phone, Set.of(Role.TEACHER), null, null);
        String code = requestCodeFor(phone, OtpPurpose.SIGN_IN, null);

        Raw verified = postRaw(
                "/api/v1/auth/otp/verify", new OtpDtos.OtpVerifyRequest(phone, code, OtpPurpose.SIGN_IN, null), null);

        assertThat(verified.status()).isEqualTo(200);
        assertThat(verified.hasRenewalCookie()).isTrue();
        assertThat(verified.map()).doesNotContainKey("renewalCredential");
    }

    @Test
    void aWrongCodeIsRejectedTheSameWay() {
        String phone = nextPhone();
        userAdminService.createUser("OTP Wrong", phone, Set.of(Role.MANAGER), null, null);
        requestCodeFor(phone, OtpPurpose.SIGN_IN, ANDROID);

        Raw verified = postRaw(
                "/api/v1/auth/otp/verify",
                new OtpDtos.OtpVerifyRequest(phone, "000000", OtpPurpose.SIGN_IN, null),
                ANDROID);

        assertThat(verified.status()).isEqualTo(401);
        assertThat(verified.map()).doesNotContainKey("renewalCredential");
    }

    @Test
    void passwordResetVerificationIsUnchangedEvenForAndroid() {
        String phone = nextPhone();
        userAdminService.createUser("Reset Teacher", phone, Set.of(Role.TEACHER), null, PASSWORD);
        String code = requestCodeFor(phone, OtpPurpose.PASSWORD_RESET, ANDROID);

        Raw verified = postRaw(
                "/api/v1/auth/otp/verify",
                new OtpDtos.OtpVerifyRequest(phone, code, OtpPurpose.PASSWORD_RESET, OtpChannel.SMS),
                ANDROID);

        assertThat(verified.status()).isEqualTo(200);
        assertThat(verified.map()).containsKey("resetToken").doesNotContainKey("renewalCredential");
        assertThat(verified.setCookies()).isEmpty();
    }
}
