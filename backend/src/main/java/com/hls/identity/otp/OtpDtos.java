package com.hls.identity.otp;

/** Request/response shapes for {@code contracts/auth-api.md}'s OTP endpoints. */
public final class OtpDtos {

    private OtpDtos() {}

    public record OtpRequest(String destination, OtpChannel channel, OtpPurpose purpose) {}

    /**
     * @param channel required for {@code PASSWORD_RESET} (must match the channel used at request
     *     time); ignored for {@code SIGN_IN}, which is always SMS.
     */
    public record OtpVerifyRequest(String destination, String code, OtpPurpose purpose, OtpChannel channel) {}

    public record NeutralResponse(String message) {}

    public record ResetTokenResponse(String resetToken) {}
}
