package com.hls.identity.preferences;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * A signed-in user's own theme (spec 039, contracts/user-theme-api.md). Always acts on the caller; there is
 * no user id and no permission key, like change-password.
 */
@RestController
public class UserThemeController {

    public record ContrastCheckRequest(String accent, String preset) {}

    private final UserThemeService themes;

    public UserThemeController(UserThemeService themes) {
        this.themes = themes;
    }

    @GetMapping("/api/v1/me/theme")
    public Map<String, Object> get(@AuthenticationPrincipal Jwt jwt) {
        return themes.get(userId(jwt));
    }

    @PutMapping("/api/v1/me/theme")
    public Map<String, Object> put(
            @RequestBody UserThemeService.ThemeRequest request, @AuthenticationPrincipal Jwt jwt) {
        return themes.put(userId(jwt), request);
    }

    @DeleteMapping("/api/v1/me/theme")
    public ResponseEntity<Void> reset(@AuthenticationPrincipal Jwt jwt) {
        themes.reset(userId(jwt));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/v1/me/theme/contrast-check")
    public Map<String, Object> contrastCheck(@RequestBody ContrastCheckRequest request) {
        return themes.contrastCheck(request.accent(), request.preset());
    }

    @ExceptionHandler(UserThemeService.ThemeInputException.class)
    ResponseEntity<Map<String, Object>> invalid(UserThemeService.ThemeInputException e) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", e.code());
        body.put("reason", e.getMessage());
        body.putAll(e.details());
        return ResponseEntity.unprocessableEntity().body(body);
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
