package com.hls.identity.auth;

import com.hls.identity.auth.AuthDtos.AuthResponse;
import com.hls.identity.auth.AuthDtos.ErrorResponse;
import com.hls.identity.auth.AuthDtos.LoginRequest;
import com.hls.identity.auth.AuthDtos.SignedInUser;
import com.hls.identity.loginhistory.LoginEventType;
import com.hls.identity.loginhistory.LoginHistoryPublisher;
import com.hls.identity.loginhistory.LoginMethod;
import com.hls.identity.otp.OtpChannel;
import com.hls.identity.session.SessionService;
import com.hls.identity.user.AppUser;
import com.hls.identity.user.AppUserRepository;
import com.hls.identity.user.Role;
import com.hls.identity.user.RoleAssignmentRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Password sign-in/logout (any role, User Story 1) and password-reset completion
 * (contracts/auth-api.md, User Story 5).
 */
@RestController
public class AuthController {

    private static final String GENERIC_INVALID_CREDENTIALS_MESSAGE =
            "Your phone number/username or password is incorrect.";
    private static final java.time.format.DateTimeFormatter LOCK_MESSAGE_TIME_FORMAT =
            java.time.format.DateTimeFormatter.ofPattern("HH:mm").withZone(java.time.ZoneId.systemDefault());

    public record LockedResponse(String message, java.time.Instant unlockAt) {}

    private final PasswordAuthService passwordAuthService;
    private final PasswordResetService passwordResetService;
    private final SessionService sessionService;
    private final JwtTokenProvider jwtTokenProvider;
    private final LoginHistoryPublisher loginHistoryPublisher;
    private final AppUserRepository appUserRepository;
    private final RoleAssignmentRepository roleAssignmentRepository;
    private final boolean cookieSecure;
    private final long renewalTtlDays;

    public AuthController(
            PasswordAuthService passwordAuthService,
            PasswordResetService passwordResetService,
            SessionService sessionService,
            JwtTokenProvider jwtTokenProvider,
            LoginHistoryPublisher loginHistoryPublisher,
            AppUserRepository appUserRepository,
            RoleAssignmentRepository roleAssignmentRepository,
            @Value("${hls.security.cookie.secure:true}") boolean cookieSecure,
            @Value("${hls.session.renewal-ttl-days:14}") long renewalTtlDays) {
        this.passwordAuthService = passwordAuthService;
        this.passwordResetService = passwordResetService;
        this.sessionService = sessionService;
        this.jwtTokenProvider = jwtTokenProvider;
        this.loginHistoryPublisher = loginHistoryPublisher;
        this.appUserRepository = appUserRepository;
        this.roleAssignmentRepository = roleAssignmentRepository;
        this.cookieSecure = cookieSecure;
        this.renewalTtlDays = renewalTtlDays;
    }

    @PostMapping("/api/v1/auth/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        String clientIp = httpRequest.getRemoteAddr();
        String deviceDescription = deviceDescriptionOf(httpRequest);

        PasswordAuthService.Result result =
                passwordAuthService.authenticate(request.identifier(), request.password());
        if (result.locked()) {
            loginHistoryPublisher.record(
                    result.userId(),
                    result.identifierNormalized(),
                    LoginMethod.PASSWORD,
                    LoginEventType.LOCKOUT,
                    "Account locked until " + result.lockUntil(),
                    clientIp,
                    deviceDescription);
            String unlockTime = LOCK_MESSAGE_TIME_FORMAT.format(result.lockUntil());
            return ResponseEntity.status(423)
                    .body(new LockedResponse("Account locked until " + unlockTime + ".", result.lockUntil()));
        }
        if (!result.success()) {
            loginHistoryPublisher.record(
                    result.userId(),
                    result.identifierNormalized(),
                    LoginMethod.PASSWORD,
                    LoginEventType.SIGN_IN_FAILURE,
                    "Invalid credentials",
                    clientIp,
                    deviceDescription);
            return ResponseEntity.status(401).body(new ErrorResponse(GENERIC_INVALID_CREDENTIALS_MESSAGE));
        }

        SessionService.CreatedSession created =
                sessionService.createSession(result.user().getId(), deviceDescription);
        String accessToken =
                jwtTokenProvider.issueAccessToken(result.user().getId(), created.session().getId(), result.roles());

        loginHistoryPublisher.record(
                result.user().getId(),
                result.identifierNormalized(),
                LoginMethod.PASSWORD,
                LoginEventType.SIGN_IN_SUCCESS,
                "Signed in",
                clientIp,
                deviceDescription);

        return ResponseEntity.ok()
                .header(
                        "Set-Cookie",
                        RenewalCookies.issue(created.plainRenewalCredential(), cookieSecure, renewalTtlDays)
                                .toString())
                .body(new AuthResponse(
                        accessToken,
                        jwtTokenProvider.getAccessTokenTtlSeconds(),
                        new SignedInUser(
                                result.user().getId(),
                                result.user().getDisplayName(),
                                result.roles().stream().map(Role::name).toList())));
    }

    @PostMapping("/api/v1/auth/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal Jwt jwt, HttpServletRequest httpRequest) {
        UUID userId = UUID.fromString(jwt.getSubject());
        UUID sessionId = UUID.fromString(jwt.getClaimAsString("sid"));
        sessionService.endSession(sessionId);
        String phone = appUserRepository.findById(userId).map(u -> u.getPhone()).orElse(null);
        loginHistoryPublisher.record(
                userId,
                phone,
                LoginMethod.PASSWORD,
                LoginEventType.LOGOUT,
                "Logged out",
                httpRequest.getRemoteAddr(),
                deviceDescriptionOf(httpRequest));
        return ResponseEntity.noContent()
                .header("Set-Cookie", RenewalCookies.expire(cookieSecure).toString())
                .build();
    }

    @PostMapping("/api/v1/auth/renew")
    public ResponseEntity<?> renew(
            @CookieValue(name = RenewalCookies.COOKIE_NAME, required = false) String renewalCredential,
            HttpServletRequest httpRequest) {
        if (renewalCredential == null) {
            return ResponseEntity.status(401).body(new ErrorResponse("Please sign in again."));
        }

        SessionService.RenewResult result = sessionService.renew(renewalCredential);
        switch (result.outcome()) {
            case REUSE_DETECTED -> {
                String phone = appUserRepository
                        .findById(result.userId())
                        .map(u -> u.getPhone())
                        .orElse(null);
                loginHistoryPublisher.record(
                        result.userId(),
                        phone,
                        LoginMethod.RENEWAL,
                        LoginEventType.SESSION_REVOKED_REUSE,
                        "Renewal credential reuse detected; session revoked",
                        httpRequest.getRemoteAddr(),
                        deviceDescriptionOf(httpRequest));
                return ResponseEntity.status(401)
                        .header("Set-Cookie", RenewalCookies.expire(cookieSecure).toString())
                        .body(new ErrorResponse("Please sign in again."));
            }
            case EXPIRED, INVALID -> {
                return ResponseEntity.status(401)
                        .header("Set-Cookie", RenewalCookies.expire(cookieSecure).toString())
                        .body(new ErrorResponse("Please sign in again."));
            }
            case SUCCESS -> {
                List<Role> roles = roleAssignmentRepository
                        .findByUserId(result.session().getUserId())
                        .stream()
                        .map(ra -> ra.getRole())
                        .toList();
                String accessToken = jwtTokenProvider.issueAccessToken(
                        result.session().getUserId(), result.session().getId(), roles);
                AppUser user = appUserRepository.findById(result.session().getUserId()).orElseThrow();
                return ResponseEntity.ok()
                        .header(
                                "Set-Cookie",
                                RenewalCookies.issue(result.plainRenewalCredential(), cookieSecure, renewalTtlDays)
                                        .toString())
                        .body(new AuthResponse(
                                accessToken,
                                jwtTokenProvider.getAccessTokenTtlSeconds(),
                                new SignedInUser(user.getId(), user.getDisplayName(), roles.stream()
                                        .map(Role::name)
                                        .toList())));
            }
        }
        throw new IllegalStateException("Unreachable");
    }

    @GetMapping("/api/v1/auth/password-reset/channels")
    public ResponseEntity<Set<OtpChannel>> resetChannels(@RequestParam String identifier) {
        return ResponseEntity.ok(passwordResetService.availableChannels(identifier));
    }

    public record CompletePasswordResetRequest(String resetToken, String newPassword) {}

    @PostMapping("/api/v1/auth/password-reset/complete")
    public ResponseEntity<?> completeReset(
            @RequestBody CompletePasswordResetRequest request, HttpServletRequest httpRequest) {
        PasswordResetService.CompleteOutcome outcome = passwordResetService.complete(
                request.resetToken(),
                request.newPassword(),
                httpRequest.getRemoteAddr(),
                deviceDescriptionOf(httpRequest));
        return switch (outcome) {
            case SUCCESS -> ResponseEntity.ok().build();
            case PASSWORD_POLICY_VIOLATION -> ResponseEntity.status(400)
                    .body(new ErrorResponse(PasswordPolicy.VIOLATION_MESSAGE));
            case INVALID_OR_EXPIRED_TOKEN -> ResponseEntity.status(400)
                    .body(new ErrorResponse("This reset link has expired. Please request a new one."));
        };
    }

    private static String deviceDescriptionOf(HttpServletRequest request) {
        String userAgent = request.getHeader("User-Agent");
        return userAgent != null ? userAgent : "Unknown device";
    }
}
