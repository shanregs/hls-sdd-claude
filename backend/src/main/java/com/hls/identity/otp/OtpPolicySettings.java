package com.hls.identity.otp;

import com.hls.identity.policy.RuntimeSettings;

/**
 * The OTP request throttling policy (FR-028/FR-029) as one immutable value, taken from Security Settings
 * (spec 011) at the moment of a request, so a change applies to the next request with no restart.
 */
public class OtpPolicySettings {

    private final int resendCooldownSeconds;
    private final int maxConsecutiveRequests;
    private final int consecutiveRequestLockoutHours;

    OtpPolicySettings(int resendCooldownSeconds, int maxConsecutiveRequests, int consecutiveRequestLockoutHours) {
        this.resendCooldownSeconds = resendCooldownSeconds;
        this.maxConsecutiveRequests = maxConsecutiveRequests;
        this.consecutiveRequestLockoutHours = consecutiveRequestLockoutHours;
    }

    static OtpPolicySettings from(RuntimeSettings settings) {
        return new OtpPolicySettings(
                settings.otpResendCooldownSeconds(),
                settings.otpMaxConsecutiveRequests(),
                settings.otpLockoutHours());
    }

    public int getResendCooldownSeconds() {
        return resendCooldownSeconds;
    }

    public int getMaxConsecutiveRequests() {
        return maxConsecutiveRequests;
    }

    public int getConsecutiveRequestLockoutHours() {
        return consecutiveRequestLockoutHours;
    }
}
