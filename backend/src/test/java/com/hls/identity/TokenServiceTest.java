package com.hls.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.hls.identity.internal.Channel;
import com.hls.identity.internal.IdentityProperties;
import com.hls.identity.internal.Role;
import com.hls.identity.internal.SessionRepository;
import com.hls.identity.internal.TokenService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.Set;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit coverage for specs/005-teacher research.md §3: the additive
 * "teacherId" access-token claim. {@link SessionRepository} is mocked; this
 * is a pure claim-content check, not a persistence test.
 */
class TokenServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-22T10:00:00Z");
    private static final String SIGNING_KEY = "a-test-signing-key-that-is-long-enough-for-hs256-1234567890";

    private TokenService tokenService;
    private SecretKey signingKey;

    @BeforeEach
    void setUp() {
        SessionRepository sessionRepository = mock(SessionRepository.class);
        IdentityProperties properties = new IdentityProperties();
        properties.setJwtSigningKey(SIGNING_KEY);
        properties.setAccessTokenTtlSeconds(900);
        properties.setRefreshTokenTtlSeconds(3600);
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        tokenService = new TokenService(sessionRepository, properties, clock);
        signingKey = Keys.hmacShaKeyFor(SIGNING_KEY.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void buildAccessToken_whenLinkedTeacherIdSet_includesTeacherIdClaim() {
        UUID linkedTeacherId = UUID.randomUUID();

        TokenService.IssuedTokens issued = tokenService.issueForNewSession(
                UUID.randomUUID(), Set.of(Role.TEACHER), Channel.MOBILE, "device-1", linkedTeacherId);

        // A fixed parser clock, matching the fixed issuance clock — otherwise validation
        // checks "exp" against the real wall clock, which makes this test flaky/expired
        // once real time drifts away from the fixed NOW used to build the token.
        String teacherIdClaim = Jwts.parser().verifyWith(signingKey).clock(() -> Date.from(NOW)).build()
                .parseSignedClaims(issued.accessToken()).getPayload().get("teacherId", String.class);
        assertThat(teacherIdClaim).isEqualTo(linkedTeacherId.toString());
    }

    @Test
    void buildAccessToken_whenLinkedTeacherIdNull_omitsTeacherIdClaim() {
        TokenService.IssuedTokens issued = tokenService.issueForNewSession(
                UUID.randomUUID(), Set.of(Role.MANAGER), Channel.WEB, null, null);

        String teacherIdClaim = Jwts.parser().verifyWith(signingKey).clock(() -> Date.from(NOW)).build()
                .parseSignedClaims(issued.accessToken()).getPayload().get("teacherId", String.class);
        assertThat(teacherIdClaim).isNull();
    }
}
