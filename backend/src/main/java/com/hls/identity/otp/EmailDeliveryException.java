package com.hls.identity.otp;

/** Thrown by an {@link EmailGateway} when a code could not be sent — same treatment as
 * {@link SmsDeliveryException} (spec.md's analogous "email gateway is unavailable" edge case). */
public class EmailDeliveryException extends RuntimeException {
    public EmailDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
