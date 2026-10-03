package com.hls.identity.otp;

/**
 * Thrown by an {@link SmsGateway} when a code could not be sent (the gateway is unreachable,
 * rejects the request, etc.) — the spec.md edge case "the SMS gateway is unavailable": the caller
 * is told delivery failed rather than getting the neutral confirmation, and the attempt does not
 * count toward the rate limit (FR-007).
 */
public class SmsDeliveryException extends RuntimeException {
    public SmsDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
