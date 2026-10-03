package com.hls.identity.otp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Sends an OTP by SMS. MSG91 is the selected provider for India (research.md §1, §15): India-native
 * INR billing avoids the forex surcharge global providers like Twilio add, and it has a mature
 * DLT-template workflow, which India mandates for transactional/OTP SMS. See {@link Msg91SmsGateway}.
 * Active only when {@code hls.sms.provider=msg91}; the dev-stub below is the default otherwise.
 */
public interface SmsGateway {

    /** @throws SmsDeliveryException if the code could not be sent. */
    void sendCode(String phone, String code);

    @Component
    @ConditionalOnProperty(name = "hls.sms.provider", havingValue = "dev-stub", matchIfMissing = true)
    class DevStubSmsGateway implements SmsGateway {
        private static final Logger log = LoggerFactory.getLogger(DevStubSmsGateway.class);

        @Override
        public void sendCode(String phone, String code) {
            log.info("[DEV SMS STUB] To {}: your HLS sign-in/reset code is {}", phone, code);
        }
    }
}
