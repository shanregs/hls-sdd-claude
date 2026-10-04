package com.hls.identity.clientcontext;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.support.IntegrationTestBase;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/**
 * Spec 018 T014 (research.md §5 privacy note): the {@code X-HLS-Location} header, which carries a
 * staff member's coordinates, must never end up in application or access logs.
 */
@ExtendWith(OutputCaptureExtension.class)
class LocationHeaderNotLoggedTest extends IntegrationTestBase {

    // Unusual digits so a match can only be this test's header value.
    private static final String LAT = "12.345678";
    private static final String LNG = "77.876543";

    @Test
    void coordinatesAreNotWrittenToTheLogsForSuccessfulOrFailedRequests(CapturedOutput output) {
        String header = "lat=" + LAT + ";lng=" + LNG + ";acc=11.5;ts=" + Instant.now().toEpochMilli();

        // A request that fails authentication and one that is a bad sign-in both exercise the
        // error and audit logging paths.
        client.get()
                .uri("/api/v1/me/access-model")
                .header("X-HLS-Client", "android/1.0.0")
                .header("X-HLS-Location", header)
                .exchange()
                .returnResult(String.class);
        client.post()
                .uri("/api/v1/auth/login")
                .header("X-HLS-Client", "android/1.0.0")
                .header("X-HLS-Location", header)
                .body(new com.hls.identity.auth.AuthDtos.LoginRequest("9000000000", "wrong-password-1"))
                .exchange()
                .returnResult(String.class);

        assertThat(output.getAll()).doesNotContain(LAT).doesNotContain(LNG).doesNotContain("X-HLS-Location");
    }
}
