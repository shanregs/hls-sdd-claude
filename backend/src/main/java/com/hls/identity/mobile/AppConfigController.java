package com.hls.identity.mobile;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The one public endpoint of spec 018 (FR-030): non-sensitive values the Android app needs before
 * anyone has signed in. These three settings become part of the settings module in spec 011.
 */
@RestController
public class AppConfigController {

    public record AppConfig(String minimumVersion, int locationWaitSeconds, int locationReuseSeconds) {}

    private final AppConfig config;

    public AppConfigController(
            @Value("${hls.mobile.min-app-version:0.0.0}") String minimumVersion,
            @Value("${hls.mobile.location-wait-seconds:4}") int locationWaitSeconds,
            @Value("${hls.mobile.location-reuse-seconds:10}") int locationReuseSeconds) {
        this.config = new AppConfig(
                AppVersion.parse(minimumVersion).toString(), locationWaitSeconds, locationReuseSeconds);
    }

    @GetMapping("/api/v1/mobile/app-config")
    public ResponseEntity<AppConfig> appConfig() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic())
                .body(config);
    }
}
