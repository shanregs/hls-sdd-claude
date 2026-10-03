package com.hls.identity.otp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Sends a password-reset OTP by email (research.md §12). Reset-only, never used for sign-in. This
 * dev-stub logs the code instead of sending real email, until a transactional email provider is
 * procured.
 */
public interface EmailGateway {

    void sendCode(String email, String code);

    @Component
    class DevStubEmailGateway implements EmailGateway {
        private static final Logger log = LoggerFactory.getLogger(DevStubEmailGateway.class);

        @Override
        public void sendCode(String email, String code) {
            log.info("[DEV EMAIL STUB] To {}: your HLS password-reset code is {}", email, code);
        }
    }
}
