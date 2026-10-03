package com.hls.identity.otp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hls.identity.user.AppUser;
import com.hls.identity.user.IdentifierResolver;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * A failing {@link SmsGateway} (e.g. MSG91 unreachable) MUST NOT silently look like a delivered
 * code: spec.md's "gateway is unavailable" edge case says the caller is told delivery failed, the
 * attempt does not count toward the per-minute rate limit, and no {@link OneTimeCode} row is
 * persisted for it. The resend-cooldown/consecutive-lockout throttle (FR-028/FR-029) is disabled
 * here (cooldown 0s, a high consecutive-request ceiling) so this test isolates the per-minute
 * bucket's refund behavior.
 */
class OtpServiceDeliveryFailureTest {

    @Test
    void gatewayFailureReportsDeliveryFailedAndDoesNotPersistOrConsumeRateLimit() {
        OneTimeCodeRepository repository = mock(OneTimeCodeRepository.class);
        SmsGateway failingSmsGateway = (phone, code) -> {
            throw new SmsDeliveryException("simulated MSG91 outage", null);
        };
        EmailGateway unusedEmailGateway = mock(EmailGateway.class);
        IdentifierResolver identifierResolver = mock(IdentifierResolver.class);
        AppUser activeUser = new AppUser("Teacher OTP", "9876500099", null, null);
        when(identifierResolver.resolve("9876500099"))
                .thenReturn(new IdentifierResolver.Resolution(Optional.of(activeUser), "9876500099"));
        OtpPolicySettingsRepository policyRepository = mock(OtpPolicySettingsRepository.class);
        when(policyRepository.findById(OtpPolicySettings.SINGLETON_ID))
                .thenReturn(Optional.of(new OtpPolicySettings(0, 1000, 4)));
        Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);

        OtpService otpService = new OtpService(
                repository,
                new OtpCodeHasher(),
                failingSmsGateway,
                unusedEmailGateway,
                identifierResolver,
                policyRepository,
                new OtpRequestThrottle(),
                clock);

        OtpService.RequestResult first = otpService.request("9876500099", OtpChannel.SMS, OtpPurpose.SIGN_IN);
        assertThat(first.outcome()).isEqualTo(OtpService.RequestOutcome.DELIVERY_FAILED);
        verify(repository, never()).save(any());

        // The failed attempt was refunded, so it does not eat into the 3-per-minute budget: two more
        // attempts in the same window still succeed in reaching the gateway (and failing there),
        // rather than being rejected as RATE_LIMITED.
        OtpService.RequestResult second = otpService.request("9876500099", OtpChannel.SMS, OtpPurpose.SIGN_IN);
        OtpService.RequestResult third = otpService.request("9876500099", OtpChannel.SMS, OtpPurpose.SIGN_IN);
        assertThat(second.outcome()).isEqualTo(OtpService.RequestOutcome.DELIVERY_FAILED);
        assertThat(third.outcome()).isEqualTo(OtpService.RequestOutcome.DELIVERY_FAILED);
    }
}
