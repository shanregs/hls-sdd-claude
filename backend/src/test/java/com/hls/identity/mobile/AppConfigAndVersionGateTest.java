package com.hls.identity.mobile;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/**
 * Spec 018 T015: the public app-config endpoint and the minimum-version gate (FR-029, FR-030).
 * Minimum version is 1.2.0 for this class.
 */
@TestPropertySource(properties = "hls.mobile.min-app-version=1.2.0")
class AppConfigAndVersionGateTest extends IntegrationTestBase {

    private int statusOf(String method, String uri, String clientHeader) {
        var spec = client.method(org.springframework.http.HttpMethod.valueOf(method)).uri(uri);
        if (clientHeader != null) {
            spec.header("X-HLS-Client", clientHeader);
        }
        return spec.exchange().returnResult(String.class).getStatus().value();
    }

    @Test
    void appConfigIsPublicAndReturnsOnlyNonSensitiveValues() {
        var result = client.get().uri("/api/v1/mobile/app-config").exchange().returnResult(String.class);

        assertThat(result.getStatus().value()).isEqualTo(200);
        assertThat(result.getResponseHeaders().getCacheControl()).contains("max-age=300");
        assertThat(result.getResponseBody())
                .contains("\"minimumVersion\":\"1.2.0\"")
                .contains("\"locationWaitSeconds\":4")
                .contains("\"locationReuseSeconds\":10");
    }

    @Test
    void appBelowTheMinimumGets426OnEveryEndpointExceptAppConfig() {
        assertThat(statusOf("GET", "/api/v1/mobile/app-config", "android/1.0.0")).isEqualTo(200);
        assertThat(statusOf("POST", "/api/v1/auth/login", "android/1.0.0")).isEqualTo(426);
        assertThat(statusOf("GET", "/api/v1/me/access-model", "android/1.1.9")).isEqualTo(426);
        assertThat(statusOf("GET", "/api/v1/me/profile", "android/0.9.9")).isEqualTo(426);
    }

    @Test
    void the426BodyCarriesTheCodeAndTheMinimumVersion() {
        var result = client.get()
                .uri("/api/v1/me/access-model")
                .header("X-HLS-Client", "android/1.0.0")
                .exchange()
                .returnResult(String.class);

        assertThat(result.getStatus().value()).isEqualTo(426);
        assertThat(result.getResponseBody())
                .contains("\"code\":\"APP_UPDATE_REQUIRED\"")
                .contains("\"minimumVersion\":\"1.2.0\"");
    }

    @Test
    void appAtOrAboveTheMinimumIsNotGated() {
        // Past the gate the normal rules apply: no token, so 401 (not 426).
        assertThat(statusOf("GET", "/api/v1/me/access-model", "android/1.2.0")).isEqualTo(401);
        assertThat(statusOf("GET", "/api/v1/me/access-model", "android/2.0.0")).isEqualTo(401);
        assertThat(statusOf("GET", "/api/v1/me/access-model", "android/1.10.0")).isEqualTo(401);
    }

    @Test
    void webRequestsAreNeverGated() {
        assertThat(statusOf("GET", "/api/v1/me/access-model", null)).isEqualTo(401);
        // A malformed client header is just a web request.
        assertThat(statusOf("GET", "/api/v1/me/access-model", "android/1.0")).isEqualTo(401);
    }

    @Test
    void appVersionsCompareNumericallyNotAsText() {
        assertThat(AppVersion.parse("1.10.0").compareTo(AppVersion.parse("1.9.0"))).isPositive();
        assertThat(AppVersion.parse("2.0.0").compareTo(AppVersion.parse("10.0.0"))).isNegative();
        assertThat(AppVersion.parse("1.2.0")).isEqualByComparingTo(AppVersion.parse("1.2.0"));
    }
}
