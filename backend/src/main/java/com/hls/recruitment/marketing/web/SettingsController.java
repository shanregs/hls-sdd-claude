package com.hls.recruitment.marketing.web;

import com.hls.identity.permissions.PermissionAction;
import com.hls.identity.permissions.PermissionGuard;
import com.hls.identity.permissions.PermissionModule;
import com.hls.recruitment.marketing.internal.SettingsService;
import com.hls.recruitment.marketing.internal.SettingsService.SettingsDto;
import com.hls.recruitment.marketing.internal.SettingsService.SettingsRequest;
import com.hls.school.api.CallerContext;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The marketing settings (module {@code MARKETING_SETTINGS}: Admin and Director by default). */
@RestController
@RequestMapping("/api/v1/marketing/settings")
public class SettingsController {

    private final SettingsService settings;
    private final PermissionGuard guard;

    public SettingsController(SettingsService settings, PermissionGuard guard) {
        this.settings = settings;
        this.guard = guard;
    }

    @GetMapping
    public SettingsDto get(@AuthenticationPrincipal Jwt jwt) {
        guard.require(CallerContext.roles(jwt), PermissionModule.MARKETING_SETTINGS, PermissionAction.VIEW);
        return settings.get();
    }

    @PutMapping
    public SettingsDto change(@RequestBody SettingsRequest request, @AuthenticationPrincipal Jwt jwt) {
        guard.require(CallerContext.roles(jwt), PermissionModule.MARKETING_SETTINGS, PermissionAction.EDIT);
        return settings.change(CallerContext.userId(jwt), request);
    }
}
