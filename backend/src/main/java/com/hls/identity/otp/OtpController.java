package com.hls.identity.otp;

import com.hls.identity.auth.AuthDtos;
import com.hls.identity.auth.JwtTokenProvider;
import com.hls.identity.auth.PasswordResetService;
import com.hls.identity.clientcontext.ClientContextHolder;
import com.hls.identity.loginhistory.LoginEventType;
import com.hls.identity.loginhistory.LoginHistoryPublisher;
import com.hls.identity.loginhistory.LoginMethod;
import com.hls.identity.mobile.MobileRoleEligibility;
import com.hls.identity.session.SessionService;
import com.hls.identity.user.AppUser;
import com.hls.identity.user.Role;
import com.hls.identity.user.RoleAssignmentRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * One-time code sign-in (any role, SMS only, FR-006) and the code-verification step of password
 * reset (FR-016). Every response for {@code request} is neutral regardless of eligibility
 * (FR-007).
 */
@RestController
public class OtpController {

    private final OtpService otpService;
    private final RoleAssignmentRepository roleAssignmentRepository;
    private final SessionService sessionService;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordResetService passwordResetService;
    private final LoginHistoryPublisher loginHistoryPublisher;
    private final boolean cookieSecure;
    private final long renewalTtlDays;

    public OtpController(
            OtpService otpService,
            RoleAssignmentRepository roleAssignmentRepository,
            SessionService sessionService,
            JwtTokenProvider jwtTokenProvider,
            PasswordResetService passwordResetService,
            LoginHistoryPublisher loginHistoryPublisher,
            @Value("${hls.security.cookie.secure:true}") boolean cookieSecure,
            @Value("${hls.session.renewal-ttl-days:14}") long renewalTtlDays) {
        this.otpService = otpService;
        this.roleAssignmentRepository = roleAssignmentRepository;
        this.sessionService = sessionService;
        this.jwtTokenProvider = jwtTokenProvider;
        this.passwordResetService = passwordResetService;
        this.loginHistoryPublisher = loginHistoryPublisher;
        this.cookieSecure = cookieSecure;
        this.renewalTtlDays = renewalTtlDays;
    }

    @PostMapping("/api/v1/auth/otp/request")
    public ResponseEntity<OtpDtos.NeutralResponse> request(
            @RequestBody OtpDtos.OtpRequest request, HttpServletRequest httpRequest) {
        OtpService.RequestResult result =
                otpService.request(request.destination(), request.channel(), request.purpose());
        switch (result.outcome()) {
            case RATE_LIMITED -> {
                return ResponseEntity.status(429).body(new OtpDtos.NeutralResponse("Too many requests, try again shortly."));
            }
            case RESEND_TOO_SOON -> {
                long seconds = Math.max(1, result.retryAfter().toSeconds());
                return ResponseEntity.status(429)
                        .header("Retry-After", String.valueOf(seconds))
                        .body(new OtpDtos.NeutralResponse(
                                "Please wait " + seconds + " seconds before requesting another code."));
            }
            case TOO_MANY_CONSECUTIVE_REQUESTS -> {
                long seconds = Math.max(1, result.retryAfter().toSeconds());
                long hours = Math.max(1, roundUpHours(result.retryAfter()));
                return ResponseEntity.status(429)
                        .header("Retry-After", String.valueOf(seconds))
                        .body(new OtpDtos.NeutralResponse(
                                "Too many requests. Please try again in about " + hours + " hour(s)."));
            }
            case DELIVERY_FAILED -> {
                // Not account-specific — every eligible destination hit the same gateway outage, so
                // this doesn't leak registration status (spec.md's "gateway is unavailable" edge case).
                return ResponseEntity.status(503)
                        .body(new OtpDtos.NeutralResponse("We couldn't send the code right now. Please try again shortly."));
            }
            case SENT_OR_IGNORED -> {
                // fall through to the neutral 200 below
            }
        }
        if (request.purpose() == OtpPurpose.SIGN_IN) {
            loginHistoryPublisher.record(
                    null,
                    request.destination(),
                    LoginMethod.OTP,
                    LoginEventType.OTP_REQUESTED,
                    "OTP requested",
                    httpRequest.getRemoteAddr(),
                    userAgent(httpRequest));
        }
        return ResponseEntity.ok(new OtpDtos.NeutralResponse("If this is registered, a code has been sent."));
    }

    @PostMapping("/api/v1/auth/otp/verify")
    public ResponseEntity<?> verify(@RequestBody OtpDtos.OtpVerifyRequest request, HttpServletRequest httpRequest) {
        OtpService.VerifyResult result =
                otpService.verify(request.destination(), request.code(), request.purpose(), request.channel());
        if (!result.valid()) {
            String message = result.failureReason() == OtpService.FailureReason.EXPIRED
                    ? "Code expired, request a new one."
                    : "That code is not valid.";
            if (request.purpose() == OtpPurpose.SIGN_IN) {
                loginHistoryPublisher.record(
                        null,
                        request.destination(),
                        LoginMethod.OTP,
                        LoginEventType.SIGN_IN_FAILURE,
                        message,
                        httpRequest.getRemoteAddr(),
                        userAgent(httpRequest));
            }
            return ResponseEntity.status(401).body(new AuthDtos.ErrorResponse(message));
        }

        AppUser user = result.user();
        if (request.purpose() == OtpPurpose.PASSWORD_RESET) {
            return ResponseEntity.ok(new OtpDtos.ResetTokenResponse(passwordResetService.issueResetToken(user)));
        }

        List<Role> roles =
                roleAssignmentRepository.findByUserId(user.getId()).stream().map(ra -> ra.getRole()).toList();
        if (ClientContextHolder.current().isAndroid() && !MobileRoleEligibility.isEligible(roles)) {
            loginHistoryPublisher.record(
                    user.getId(),
                    request.destination(),
                    LoginMethod.OTP,
                    LoginEventType.SIGN_IN_FAILURE,
                    MobileRoleEligibility.REFUSED_OUTCOME,
                    httpRequest.getRemoteAddr(),
                    userAgent(httpRequest));
            return MobileRoleEligibility.refusal();
        }

        SessionService.CreatedSession created = sessionService.createSession(user.getId(), userAgent(httpRequest));
        String accessToken = jwtTokenProvider.issueAccessToken(user.getId(), created.session().getId(), roles);

        loginHistoryPublisher.record(
                user.getId(),
                request.destination(),
                LoginMethod.OTP,
                LoginEventType.SIGN_IN_SUCCESS,
                "Signed in",
                httpRequest.getRemoteAddr(),
                userAgent(httpRequest));

        return com.hls.identity.auth.SignInResponses.ok(
                accessToken,
                jwtTokenProvider.getAccessTokenTtlSeconds(),
                new AuthDtos.SignedInUser(
                        user.getId(), user.getDisplayName(), roles.stream().map(Role::name).toList()),
                created.plainRenewalCredential(),
                cookieSecure,
                renewalTtlDays);
    }

    private static String userAgent(HttpServletRequest request) {
        String userAgent = request.getHeader("User-Agent");
        return userAgent != null ? userAgent : "Unknown device";
    }

    private static long roundUpHours(Duration duration) {
        long seconds = duration.toSeconds();
        return (seconds + 3599) / 3600;
    }
}
