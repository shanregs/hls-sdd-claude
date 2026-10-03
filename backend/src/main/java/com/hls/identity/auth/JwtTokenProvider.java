package com.hls.identity.auth;

import com.hls.identity.user.Role;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Issues the short-lived access token (research.md §7): a signed JWT carrying the user id, session
 * id, and resolved roles, validated statelessly by Spring Security's OAuth2 Resource Server
 * support (see {@code SecurityConfig}).
 */
@Component
public class JwtTokenProvider {

    private final SecretKey key;
    private final long accessTokenTtlSeconds;
    private final Clock clock;

    public JwtTokenProvider(
            @Value("${hls.security.jwt.secret}") String base64Secret,
            @Value("${hls.security.jwt.access-token-ttl-seconds:900}") long accessTokenTtlSeconds,
            Clock clock) {
        this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(base64Secret));
        this.accessTokenTtlSeconds = accessTokenTtlSeconds;
        this.clock = clock;
    }

    public String issueAccessToken(UUID userId, UUID sessionId, List<Role> roles) {
        Instant now = clock.instant();
        return Jwts.builder()
                .subject(userId.toString())
                .claim("sid", sessionId.toString())
                .claim("roles", roles.stream().map(Role::name).toList())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(accessTokenTtlSeconds, ChronoUnit.SECONDS)))
                .signWith(key)
                .compact();
    }

    public long getAccessTokenTtlSeconds() {
        return accessTokenTtlSeconds;
    }

    /** The same key Spring Security's {@code JwtDecoder} bean validates tokens with. */
    public SecretKey getKey() {
        return key;
    }
}
