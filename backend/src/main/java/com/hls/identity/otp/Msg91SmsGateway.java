package com.hls.identity.otp;

import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Sends OTP/reset codes via MSG91's Flow API (research.md §15): India-native INR billing (no
 * forex surcharge, unlike Twilio) and a mature DLT-template workflow, which India's telecom
 * regulator mandates for transactional/OTP SMS — an untemplated message is simply not delivered.
 *
 * <p>Requires a DLT-approved Flow template on the MSG91 dashboard with exactly one variable,
 * {@code var1}, holding the code (e.g. "Your HLS verification code is {{var1}}. Valid for 5
 * minutes."). Active only when {@code hls.sms.provider=msg91}; see application.yml for the
 * required {@code hls.sms.msg91.*} configuration.
 */
@Component
@ConditionalOnProperty(name = "hls.sms.provider", havingValue = "msg91")
public class Msg91SmsGateway implements SmsGateway {

    private final RestClient restClient;
    private final String templateId;

    public Msg91SmsGateway(
            @Value("${hls.sms.msg91.auth-key}") String authKey,
            @Value("${hls.sms.msg91.template-id}") String templateId,
            @Value("${hls.sms.msg91.base-url:https://control.msg91.com}") String baseUrl) {
        this(authKey, templateId, baseUrl, RestClient.builder());
    }

    /** Package-visible so tests can inject a {@link RestClient.Builder} bound to a mock server. */
    Msg91SmsGateway(String authKey, String templateId, String baseUrl, RestClient.Builder builder) {
        this.templateId = templateId;
        this.restClient = builder.baseUrl(baseUrl)
                .defaultHeader("authkey", authKey)
                .defaultHeader("Content-Type", "application/json")
                .defaultHeader("Accept", "application/json")
                .build();
    }

    @Override
    public void sendCode(String phone, String code) {
        // MSG91 expects the mobile prefixed with the country code, no "+" (docs.msg91.com/sms/send-sms).
        String mobileWithCountryCode = "91" + phone;
        Map<String, Object> body = Map.of(
                "template_id",
                templateId,
                "recipients",
                List.of(Map.of("mobiles", mobileWithCountryCode, "var1", code)));
        try {
            restClient.post().uri("/api/v5/flow").body(body).retrieve().toBodilessEntity();
        } catch (RestClientException e) {
            throw new SmsDeliveryException("MSG91 Flow API call failed for " + phone, e);
        }
    }
}
