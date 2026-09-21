package com.hls.identity.internal;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * FR-011: HS256 JWT issuance and refresh-token rotation (research.md §1, §5).
 * The access token embeds the session id and roles as claims so {@link ManagerScopeGuard}
 * and {@link TeacherScopeGuard} never need a DB round trip just to read them; only
 * refresh/revoke checks touch {@link Session}.
 */
@Service
public class TokenService {

    private final SessionRepository sessionRepository;
    private final IdentityProperties properties;
    private final SecretKey signingKey;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    public TokenService(SessionRepository sessionRepository, IdentityProperties properties, Clock clock) {
        this.sessionRepository = sessionRepository;
        this.properties = properties;
        this.clock = clock;
        this.signingKey = Keys.hmacShaKeyFor(properties.getJwtSigningKey().getBytes(StandardCharsets.UTF_8));
    }

    public record IssuedTokens(String accessToken, long expiresIn, String rawRefreshToken, UUID sessionId) {
    }

    /** Creates a brand-new session (login) and returns both tokens. */
    public IssuedTokens issueForNewSession(UUID userId, Set<Role> roles, Channel channel, String deviceLabel) {
        UUID sessionId = UUID.randomUUID();
        Instant now = clock.instant();
        String rawRefreshToken = randomToken();
        Instant refreshExpiry = now.plus(Duration.ofSeconds(properties.getRefreshTokenTtlSeconds()));

        Session session = new Session(sessionId, userId, channel, hash(rawRefreshToken), now, refreshExpiry, deviceLabel);
        sessionRepository.save(session);

        String accessToken = buildAccessToken(userId, roles, sessionId, now);
        return new IssuedTokens(accessToken, properties.getAccessTokenTtlSeconds(), rawRefreshToken, sessionId);
    }

    public record RefreshResult(String accessToken, long expiresIn, String rawRefreshToken) {
    }

    /**
     * FR-011's silent renewal. Rotates the refresh token (research.md §5) — the
     * previous raw value stops working the moment this succeeds, so a stolen-but-used
     * refresh token can't be replayed.
     */
    public RefreshResult refresh(String rawRefreshToken, UUID userId, Set<Role> roles) {
        Session session = sessionRepository.findByRefreshTokenHash(hash(rawRefreshToken))
                .filter(s -> s.isUsable(clock.instant()))
                .orElseThrow(InvalidSessionException::new);

        Instant now = clock.instant();
        String newRawRefreshToken = randomToken();
        Instant newExpiry = now.plus(Duration.ofSeconds(properties.getRefreshTokenTtlSeconds()));
        session.rotate(hash(newRawRefreshToken), now, newExpiry);
        sessionRepository.save(session);

        String accessToken = buildAccessToken(userId, roles, session.getId(), now);
        return new RefreshResult(accessToken, properties.getAccessTokenTtlSeconds(), newRawRefreshToken);
    }

    private String buildAccessToken(UUID userId, Set<Role> roles, UUID sessionId, Instant now) {
        List<String> roleNames = roles.stream().map(Enum::name).toList();
        Date issuedAt = Date.from(now);
        Date expiry = Date.from(now.plus(Duration.ofSeconds(properties.getAccessTokenTtlSeconds())));
        return Jwts.builder()
                .subject(userId.toString())
                .claim("sid", sessionId.toString())
                .claim("roles", roleNames)
                .issuedAt(issuedAt)
                .expiration(expiry)
                // Explicit HS256: signWith(Key) alone auto-selects the strongest
                // algorithm the key length supports (our dev key is long enough that
                // it picked HS384), which NimbusJwtDecoder's default HS256 expectation
                // then rejected as an invalid signature — found via IdentityIntegrationTest.
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    private String hash(String rawToken) {
        return TokenHashing.sha256Base64(rawToken);
    }

    private String randomToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static class InvalidSessionException extends RuntimeException {
    }
}
