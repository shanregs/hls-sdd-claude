package com.hls.identity.otp;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * Unit test for the MSG91 Flow API integration (research.md §15): request shape and the
 * gateway-failure mapping to {@link SmsDeliveryException} that {@link OtpService} relies on for
 * spec.md's "gateway is unavailable" edge case.
 */
class Msg91SmsGatewayTest {

    @Test
    void sendsTheCodeAsVar1ToTheCountryCodePrefixedNumber() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        Msg91SmsGateway gateway = new TestableMsg91SmsGateway(builder);

        server.expect(requestTo("https://control.msg91.com/api/v5/flow"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("authkey", "test-auth-key"))
                .andExpect(content()
                        .json(
                                """
                                {
                                  "template_id": "test-template-id",
                                  "recipients": [ { "mobiles": "919876543210", "var1": "123456" } ]
                                }
                                """))
                .andRespond(withSuccess("{\"type\":\"success\"}", MediaType.APPLICATION_JSON));

        gateway.sendCode("9876543210", "123456");

        server.verify();
    }

    @Test
    void wrapsAGatewayErrorAsSmsDeliveryException() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        Msg91SmsGateway gateway = new TestableMsg91SmsGateway(builder);

        server.expect(requestTo("https://control.msg91.com/api/v5/flow")).andRespond(withServerError());

        assertThatThrownBy(() -> gateway.sendCode("9876543210", "123456"))
                .isInstanceOf(SmsDeliveryException.class)
                .hasMessageContaining("9876543210");
    }

    /** Exposes a {@link RestClient.Builder}-based constructor so the test can inject a mock server. */
    private static final class TestableMsg91SmsGateway extends Msg91SmsGateway {
        TestableMsg91SmsGateway(RestClient.Builder builder) {
            super("test-auth-key", "test-template-id", "https://control.msg91.com", builder);
        }
    }
}
