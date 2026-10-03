package com.hls.identity.security;

import com.hls.identity.auth.JwtTokenProvider;
import com.hls.identity.session.Session;
import com.hls.identity.session.SessionRepository;
import com.hls.identity.user.AppUser;
import com.hls.identity.user.AppUserRepository;
import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * The fail-closed security baseline (Constitution Principle X, FR-001): every endpoint requires a
 * valid access token except the explicit public list. This spec issues and validates its own
 * JWTs (not a third-party IdP), so the resource-server decoder is backed by
 * {@link JwtTokenProvider}'s own signing key.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    // FR-001's public capabilities: password sign-in, OTP request/verify, session renewal,
    // password-reset request/completion. OTP/renew/reset endpoints are added by later user
    // stories in this same spec; listed here up front so this baseline does not need revisiting.
    private static final String[] PUBLIC_ENDPOINTS = {
        "/api/v1/auth/login",
        "/api/v1/auth/otp/request",
        "/api/v1/auth/otp/verify",
        "/api/v1/auth/renew",
        "/api/v1/auth/password-reset/channels",
        "/api/v1/auth/password-reset/complete"
    };

    private final JwtTokenProvider jwtTokenProvider;
    private final SessionRepository sessionRepository;
    private final AppUserRepository appUserRepository;

    public SecurityConfig(
            JwtTokenProvider jwtTokenProvider,
            SessionRepository sessionRepository,
            AppUserRepository appUserRepository) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.sessionRepository = sessionRepository;
        this.appUserRepository = appUserRepository;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        NimbusJwtDecoder decoder =
                NimbusJwtDecoder.withSecretKey(jwtTokenProvider.getKey()).build();
        decoder.setJwtValidator(
                new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefault(), sessionAndUserActiveValidator()));
        return decoder;
    }

    /**
     * Rejects an otherwise-valid access token whose session (the {@code sid} claim) is no longer
     * active or whose user is deactivated, so deactivation, logout and admin password reset take
     * effect on the very next request rather than when the token expires (spec 004 FR-004/FR-006).
     */
    private OAuth2TokenValidator<Jwt> sessionAndUserActiveValidator() {
        return jwt -> {
            try {
                UUID sessionId = UUID.fromString(jwt.getClaimAsString("sid"));
                UUID userId = UUID.fromString(jwt.getSubject());
                boolean sessionActive = sessionRepository
                        .findById(sessionId)
                        .filter(Session::isActive)
                        .filter(s -> s.getUserId().equals(userId))
                        .isPresent();
                boolean userActive =
                        appUserRepository.findById(userId).filter(AppUser::isActive).isPresent();
                if (sessionActive && userActive) {
                    return OAuth2TokenValidatorResult.success();
                }
            } catch (RuntimeException e) {
                // malformed sid/sub claim: fall through to failure (fail closed)
            }
            return OAuth2TokenValidatorResult.failure(
                    new OAuth2Error("invalid_token", "The session is no longer active.", null));
        };
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        JwtGrantedAuthoritiesConverter rolesConverter = new JwtGrantedAuthoritiesConverter();
        rolesConverter.setAuthoritiesClaimName("roles");
        rolesConverter.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter authenticationConverter = new JwtAuthenticationConverter();
        authenticationConverter.setJwtGrantedAuthoritiesConverter(rolesConverter);

        http
                // Bearer-token API: the access token travels only in the Authorization header, never
                // a cookie, so it cannot be replayed cross-site by a browser form/image submission.
                // The one cookie this spec sets (the renewal credential) is SameSite=Strict, which
                // is CSRF's own mitigation for cookie-carried credentials.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt
                        .decoder(jwtDecoder())
                        .jwtAuthenticationConverter(authenticationConverter)));

        return http.build();
    }
}
