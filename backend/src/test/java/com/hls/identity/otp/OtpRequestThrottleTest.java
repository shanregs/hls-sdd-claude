package com.hls.identity.otp;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * Unit test for the resend-cooldown and consecutive-request lockout (FR-028/FR-029), independent
 * of the DB/Spring wiring — {@link OtpService} just delegates to this per the decision behind
 * research.md §17.
 */
class OtpRequestThrottleTest {

    private static final OtpPolicySettings POLICY = new OtpPolicySettings(30, 5, 4);

    @Test
    void secondRequestWithinTheCooldownIsRejected() {
        OtpRequestThrottle throttle = new OtpRequestThrottle();
        Instant t0 = Instant.parse("2026-01-01T00:00:00Z");

        assertThat(throttle.evaluate("k", POLICY, t0).type()).isEqualTo(OtpRequestThrottle.Decision.Type.ALLOWED);

        OtpRequestThrottle.Decision second = throttle.evaluate("k", POLICY, t0.plusSeconds(10));
        assertThat(second.type()).isEqualTo(OtpRequestThrottle.Decision.Type.TOO_SOON);
        assertThat(second.retryAfter()).isEqualTo(Duration.ofSeconds(20));
    }

    @Test
    void requestAfterTheCooldownHasPassedIsAllowedAgain() {
        OtpRequestThrottle throttle = new OtpRequestThrottle();
        Instant t0 = Instant.parse("2026-01-01T00:00:00Z");

        throttle.evaluate("k", POLICY, t0);
        OtpRequestThrottle.Decision afterCooldown = throttle.evaluate("k", POLICY, t0.plusSeconds(31));

        assertThat(afterCooldown.type()).isEqualTo(OtpRequestThrottle.Decision.Type.ALLOWED);
    }

    @Test
    void sixthConsecutiveRequestLocksForTheConfiguredHours() {
        OtpRequestThrottle throttle = new OtpRequestThrottle();
        Instant t = Instant.parse("2026-01-01T00:00:00Z");

        // Requests 1-5 succeed, each spaced past the 30s cooldown.
        for (int i = 0; i < 5; i++) {
            t = t.plusSeconds(31);
            assertThat(throttle.evaluate("k", POLICY, t).type()).isEqualTo(OtpRequestThrottle.Decision.Type.ALLOWED);
        }

        // The 6th crosses the limit and is rejected with a 4-hour lock.
        t = t.plusSeconds(31);
        OtpRequestThrottle.Decision sixth = throttle.evaluate("k", POLICY, t);
        assertThat(sixth.type()).isEqualTo(OtpRequestThrottle.Decision.Type.LOCKED);
        assertThat(sixth.retryAfter()).isEqualTo(Duration.ofHours(4));

        // Still locked shortly after, even though the resend cooldown alone would have passed.
        OtpRequestThrottle.Decision stillLocked = throttle.evaluate("k", POLICY, t.plusSeconds(60));
        assertThat(stillLocked.type()).isEqualTo(OtpRequestThrottle.Decision.Type.LOCKED);
    }

    @Test
    void lockClearsOnceTheLockoutWindowPasses() {
        OtpRequestThrottle throttle = new OtpRequestThrottle();
        Instant t = Instant.parse("2026-01-01T00:00:00Z");
        for (int i = 0; i < 6; i++) {
            t = t.plusSeconds(31);
            throttle.evaluate("k", POLICY, t);
        }

        OtpRequestThrottle.Decision afterLockout = throttle.evaluate("k", POLICY, t.plus(Duration.ofHours(4).plusSeconds(1)));

        assertThat(afterLockout.type()).isEqualTo(OtpRequestThrottle.Decision.Type.ALLOWED);
    }

    @Test
    void successfulVerificationResetsTheCounterImmediately() {
        OtpRequestThrottle throttle = new OtpRequestThrottle();
        Instant t = Instant.parse("2026-01-01T00:00:00Z");
        for (int i = 0; i < 4; i++) {
            t = t.plusSeconds(31);
            throttle.evaluate("k", POLICY, t);
        }

        throttle.resetOnSuccessfulVerification("k");

        // Without the reset, the next request would be the 5th (still allowed) then 6th (locked) —
        // after reset, it's back to "1st" and the cooldown/limit restart from zero.
        t = t.plusSeconds(31);
        assertThat(throttle.evaluate("k", POLICY, t).type()).isEqualTo(OtpRequestThrottle.Decision.Type.ALLOWED);
    }
}
