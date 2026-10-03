package com.hls.identity.otp;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * In-memory OTP request throttling, keyed the same way as {@code OtpService}'s per-minute bucket
 * (research.md §17): a short resend cooldown, and a longer-window "too many consecutive requests"
 * lockout that only a successful verification clears. Thresholds come from
 * {@link OtpPolicySettings} (DB-backed, FR-028/FR-029); only the transient per-key state here is
 * in-memory, consistent with the single-instance deployment model (research.md §4).
 */
@Component
class OtpRequestThrottle {

    private final ConcurrentHashMap<String, State> states = new ConcurrentHashMap<>();

    Decision evaluate(String key, OtpPolicySettings policy, Instant now) {
        State state = states.computeIfAbsent(key, k -> new State());
        synchronized (state) {
            if (state.lockedUntil != null) {
                if (now.isBefore(state.lockedUntil)) {
                    return Decision.locked(Duration.between(now, state.lockedUntil));
                }
                // Lockout window has passed: start counting fresh.
                state.lockedUntil = null;
                state.consecutiveCount = 0;
            }

            if (state.lastRequestAt != null) {
                Duration cooldown = Duration.ofSeconds(policy.getResendCooldownSeconds());
                Duration sinceLast = Duration.between(state.lastRequestAt, now);
                if (sinceLast.compareTo(cooldown) < 0) {
                    return Decision.tooSoon(cooldown.minus(sinceLast));
                }
            }

            state.lastRequestAt = now;
            state.consecutiveCount++;
            if (state.consecutiveCount > policy.getMaxConsecutiveRequests()) {
                state.lockedUntil = now.plus(Duration.ofHours(policy.getConsecutiveRequestLockoutHours()));
                return Decision.locked(Duration.between(now, state.lockedUntil));
            }
            return Decision.allowed();
        }
    }

    /** Called on a successful verification: the user proved they're legitimate, so their
     * consecutive-request count (and any lock) is cleared (FR-029). */
    void resetOnSuccessfulVerification(String key) {
        states.remove(key);
    }

    private static final class State {
        Instant lastRequestAt;
        int consecutiveCount;
        Instant lockedUntil;
    }

    record Decision(Type type, Duration retryAfter) {
        enum Type {
            ALLOWED,
            TOO_SOON,
            LOCKED
        }

        static Decision allowed() {
            return new Decision(Type.ALLOWED, Duration.ZERO);
        }

        static Decision tooSoon(Duration retryAfter) {
            return new Decision(Type.TOO_SOON, retryAfter);
        }

        static Decision locked(Duration retryAfter) {
            return new Decision(Type.LOCKED, retryAfter);
        }
    }
}
