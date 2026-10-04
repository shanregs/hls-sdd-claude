package com.hls.audit.apiaccess;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.support.MobileTestBase;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/**
 * Spec 018 T090 (E2): a request the version gate answers 426 is still recorded, because the API
 * Access filter is the outermost one. Minimum version is 5.0.0 for this class.
 */
@TestPropertySource(properties = "hls.mobile.min-app-version=5.0.0")
class ApiAccessGatedRequestTest extends MobileTestBase {

    @Test
    void aRequestTheVersionGateRefusesIsRecordedWith426() {
        Raw refused = getRaw(
                "/api/v1/me/profile", null, "android/1.0.0", Map.of("X-HLS-Location-Status", "SERVICES_OFF"));

        assertThat(refused.status()).isEqualTo(426);
        List<Map<String, Object>> rows = awaitRows(
                "SELECT * FROM api_access_entry WHERE status_code = 426 AND app_version = '1.0.0'"
                        + " AND location_status = 'SERVICES_OFF'",
                1);
        assertThat(rows.get(0).get("user_id")).isNull();
        assertThat(rows.get(0).get("http_method")).isEqualTo("GET");
    }

    @Test
    void theAppConfigRequestIsStillNotRecordedEvenWhenTheAppIsTooOld() {
        Raw config = getRaw("/api/v1/mobile/app-config", null, "android/1.0.0", Map.of());
        // A marker request that is recorded, so we know the consumer has caught up.
        getRaw("/api/v1/me/access-model", null, "android/1.0.0", Map.of());

        assertThat(config.status()).isEqualTo(200);
        awaitRows("SELECT * FROM api_access_entry WHERE route_template = 'UNMATCHED' AND status_code = 426", 1);
        Integer recorded = jdbc.queryForObject(
                "SELECT COUNT(*) FROM api_access_entry WHERE route_template = '/api/v1/mobile/app-config'", Integer.class);
        assertThat(recorded).isZero();
    }
}
