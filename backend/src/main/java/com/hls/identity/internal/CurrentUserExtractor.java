package com.hls.identity.internal;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Reads the {@code (userId, roles)} claims {@link TokenService} put on every access token. */
@Component
public class CurrentUserExtractor {

    public UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    @SuppressWarnings("unchecked")
    public Set<Role> roles(Jwt jwt) {
        List<String> roleNames = jwt.getClaimAsStringList("roles");
        if (roleNames == null) {
            return Set.of();
        }
        return roleNames.stream().map(Role::valueOf).collect(Collectors.toSet());
    }

    public UUID sessionId(Jwt jwt) {
        return UUID.fromString(jwt.getClaimAsString("sid"));
    }
}
