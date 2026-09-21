package com.hls.identity.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Dev/test implementation (research.md §3): logs the code instead of calling a
 * real gateway, and — critically — this code is never returned in any API
 * response (FR-015). Replace with a real India SMS/email gateway integration
 * (Requirements §12) without changing {@link OtpSender}'s contract or any caller.
 */
@Component
public class LoggingOtpSender implements OtpSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingOtpSender.class);

    @Override
    public void sendCode(String identifier, String code) {
        log.info("DEV OTP SENDER (not a real gateway): identifier={} code={}", identifier, code);
    }
}
