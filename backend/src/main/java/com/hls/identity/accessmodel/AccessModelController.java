package com.hls.identity.accessmodel;

import com.hls.identity.user.Role;
import java.util.EnumSet;
import java.util.Set;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** {@code GET /api/v1/me/access-model} (contracts/access-model-api.md): any authenticated caller. */
@RestController
public class AccessModelController {

    private final AccessModelService accessModelService;

    public AccessModelController(AccessModelService accessModelService) {
        this.accessModelService = accessModelService;
    }

    @GetMapping("/api/v1/me/access-model")
    public ResponseEntity<AccessModelDtos.AccessModelResponse> getAccessModel(@AuthenticationPrincipal Jwt jwt) {
        Set<Role> roles = EnumSet.noneOf(Role.class);
        for (String role : jwt.getClaimAsStringList("roles")) {
            roles.add(Role.valueOf(role));
        }
        return ResponseEntity.ok(accessModelService.resolve(roles));
    }
}
