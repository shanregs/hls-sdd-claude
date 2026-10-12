package com.hls.identity.mobile;

import com.hls.identity.policy.RuntimeSettings;
import java.time.Duration;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The one public endpoint of spec 018 (FR-030): non-sensitive values the Android app needs before
 * anyone has signed in. The three values are System Settings (spec 011), read on every call.
 */
@RestController
public class AppConfigController {

    public record AppConfig(String minimumVersion, int locationWaitSeconds, int locationReuseSeconds) {}

    private final RuntimeSettings settings;

    public AppConfigController(RuntimeSettings settings) {
        this.settings = settings;
    }

    @GetMapping("/api/v1/mobile/app-config")
    public ResponseEntity<AppConfig> appConfig() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic())
                .body(new AppConfig(
                        AppVersion.parse(settings.minAppVersion()).toString(),
                        settings.locationWaitSeconds(),
                        settings.locationReuseSeconds()));
    }
}
