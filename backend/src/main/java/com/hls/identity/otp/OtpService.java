package com.hls.identity.otp;

import com.hls.identity.user.AppUser;
import com.hls.identity.user.IdentifierResolver;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Requests and verifies one-time codes for sign-in (SMS only, any role, {@code input} is the
 * phone number directly) and password reset (SMS, email, or both at once, research.md §14;
 * {@code input} is a phone-or-username identifier resolved server-side to the account's actual
 * phone/email — the caller never needs to already know it, FR-006/FR-016).
 */
@Service
public class OtpService {

    private static final Logger log = LoggerFactory.getLogger(OtpService.class);

    private static final int CODE_TTL_MINUTES = 5;
    private static final int MAX_WRONG_ATTEMPTS = 5;
    private static final int MAX_REQUESTS_PER_MINUTE = 3;

    private final OneTimeCodeRepository repository;
    private final OtpCodeHasher hasher;
    private final SmsGateway smsGateway;
    private final EmailGateway emailGateway;
    private final IdentifierResolver identifierResolver;
    private final OtpPolicySettingsRepository policyRepository;
    private final OtpRequestThrottle throttle;
    private final Clock clock;
    private final ConcurrentMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    public OtpService(
            OneTimeCodeRepository repository,
            OtpCodeHasher hasher,
            SmsGateway smsGateway,
            EmailGateway emailGateway,
            IdentifierResolver identifierResolver,
            OtpPolicySettingsRepository policyRepository,
            OtpRequestThrottle throttle,
            Clock clock) {
        this.repository = repository;
        this.hasher = hasher;
        this.smsGateway = smsGateway;
        this.emailGateway = emailGateway;
        this.identifierResolver = identifierResolver;
        this.policyRepository = policyRepository;
        this.throttle = throttle;
        this.clock = clock;
    }

    /**
     * @param input for {@code SIGN_IN}, the phone number to text; for {@code PASSWORD_RESET}, a
     *     phone-or-username identifier — the actual phone/email destination(s) are resolved
     *     server-side, so the caller never has to already know them.
     * @param requestedChannel ignored for {@code SIGN_IN} (always SMS); for {@code PASSWORD_RESET}
     *     one of {@code SMS}, {@code EMAIL}, or {@code BOTH} (research.md §14).
     */
    @Transactional
    public RequestResult request(String input, OtpChannel requestedChannel, OtpPurpose purpose) {
        OtpChannel channel = purpose == OtpPurpose.SIGN_IN ? OtpChannel.SMS : requestedChannel;
        String key = purpose + ":" + channel + ":" + input;
        Instant now = clock.instant();

        // FR-028/FR-029: resend cooldown and the longer consecutive-requests lockout, both
        // DB-configurable (research.md §17). Checked before the per-minute bucket below, since
        // they're independent, coarser guards.
        OtpPolicySettings policy = policyRepository
                .findById(OtpPolicySettings.SINGLETON_ID)
                .orElseThrow(() -> new IllegalStateException("otp_policy_settings row is missing"));
        OtpRequestThrottle.Decision decision = throttle.evaluate(key, policy, now);
        if (decision.type() == OtpRequestThrottle.Decision.Type.TOO_SOON) {
            return RequestResult.of(RequestOutcome.RESEND_TOO_SOON, decision.retryAfter());
        }
        if (decision.type() == OtpRequestThrottle.Decision.Type.LOCKED) {
            return RequestResult.of(RequestOutcome.TOO_MANY_CONSECUTIVE_REQUESTS, decision.retryAfter());
        }

        Bucket bucket = buckets.computeIfAbsent(key, k -> Bucket.builder()
                .addLimit(Bandwidth.simple(MAX_REQUESTS_PER_MINUTE, Duration.ofMinutes(1)))
                .build());
        if (!bucket.tryConsume(1)) {
            return RequestResult.of(RequestOutcome.RATE_LIMITED, Duration.ZERO);
        }

        List<Resolved> resolved = resolveDestinations(input, channel, purpose);
        if (resolved.isEmpty()) {
            return RequestResult.of(RequestOutcome.SENT_OR_IGNORED, Duration.ZERO);
        }

        String code = hasher.generateCode();
        String codeHash = hasher.hash(code);
        Instant expiresAt = now.plus(CODE_TTL_MINUTES, ChronoUnit.MINUTES);
        boolean anyDelivered = false;
        for (Resolved r : resolved) {
            try {
                if (r.channel() == OtpChannel.SMS) {
                    smsGateway.sendCode(r.destination(), code);
                } else {
                    emailGateway.sendCode(r.destination(), code);
                }
                repository.save(new OneTimeCode(r.channel(), r.destination(), purpose, codeHash, expiresAt));
                anyDelivered = true;
            } catch (SmsDeliveryException | EmailDeliveryException e) {
                // spec.md edge case: "the gateway is unavailable" — recorded, not silently dropped,
                // and (below) refunded so it doesn't count toward the rate limit as a delivered code.
                log.warn("OTP delivery failed via {} for purpose {}", r.channel(), purpose, e);
            }
        }
        if (!anyDelivered) {
            bucket.addTokens(1);
            return RequestResult.of(RequestOutcome.DELIVERY_FAILED, Duration.ZERO);
        }
        return RequestResult.of(RequestOutcome.SENT_OR_IGNORED, Duration.ZERO);
    }

    @Transactional
    public VerifyResult verify(String input, String code, OtpPurpose purpose, OtpChannel channelHint) {
        OtpChannel channel = purpose == OtpPurpose.SIGN_IN ? OtpChannel.SMS : channelHint;
        List<Resolved> resolved = resolveDestinations(input, channel, purpose);
        if (resolved.isEmpty()) {
            return VerifyResult.invalid();
        }

        String codeHash = hasher.hash(code);
        Instant now = clock.instant();
        boolean sawExpired = false;

        for (Resolved r : resolved) {
            List<OneTimeCode> candidates =
                    repository.findByDestinationAndPurposeOrderByExpiresAtDesc(r.destination(), purpose);
            Optional<OneTimeCode> maybeLatestUnused =
                    candidates.stream().filter(c -> !c.isUsed()).findFirst();
            if (maybeLatestUnused.isEmpty()) {
                continue;
            }
            OneTimeCode otc = maybeLatestUnused.get();
            if (otc.isExpired(now)) {
                sawExpired = true;
                continue;
            }
            if (!otc.getCodeHash().equals(codeHash)) {
                otc.incrementWrongAttempts();
                if (otc.getWrongAttemptCount() >= MAX_WRONG_ATTEMPTS) {
                    otc.markUsed(now);
                }
                repository.save(otc);
                continue;
            }
            // Match: mark this row and every sibling created by the same BOTH request as used
            // (research.md §14), so the code cannot complete a second reset from the other channel.
            // Siblings are only this person's own destinations; another user's code that happens to be
            // the same number must not be used up.
            Set<String> ownDestinations =
                    resolved.stream().map(Resolved::destination).collect(Collectors.toSet());
            for (OneTimeCode sibling :
                    repository.findByDestinationInAndPurposeAndCodeHash(ownDestinations, purpose, codeHash)) {
                if (!sibling.isUsed()) {
                    sibling.markUsed(now);
                    repository.save(sibling);
                }
            }
            // FR-029: a successful verification proves the requester is legitimate, so their
            // consecutive-request count (and any lock) is cleared.
            throttle.resetOnSuccessfulVerification(purpose + ":" + channel + ":" + input);
            return VerifyResult.success(r.user());
        }
        return sawExpired ? VerifyResult.expired() : VerifyResult.invalid();
    }

    /** Resolves {@code input}/{@code channel}/{@code purpose} into the destination(s) to act on. */
    private List<Resolved> resolveDestinations(String input, OtpChannel channel, OtpPurpose purpose) {
        if (purpose == OtpPurpose.SIGN_IN) {
            IdentifierResolver.Resolution resolution = identifierResolver.resolve(input);
            return resolution
                    .user()
                    .filter(AppUser::isActive)
                    .map(u -> List.of(new Resolved(OtpChannel.SMS, resolution.normalizedIdentifier(), u)))
                    .orElseGet(List::of);
        }

        Optional<AppUser> maybeUser =
                identifierResolver.resolve(input).user().filter(AppUser::isActive);
        if (maybeUser.isEmpty()) {
            return List.of();
        }
        AppUser user = maybeUser.get();

        List<Resolved> resolved = new ArrayList<>();
        if (channel == OtpChannel.SMS || channel == OtpChannel.BOTH) {
            resolved.add(new Resolved(OtpChannel.SMS, user.getPhone(), user));
        }
        if ((channel == OtpChannel.EMAIL || channel == OtpChannel.BOTH) && user.getEmail() != null) {
            resolved.add(new Resolved(OtpChannel.EMAIL, user.getEmail(), user));
        }
        return resolved;
    }

    private record Resolved(OtpChannel channel, String destination, AppUser user) {}

    public enum RequestOutcome {
        SENT_OR_IGNORED,
        RATE_LIMITED,
        DELIVERY_FAILED,
        RESEND_TOO_SOON,
        TOO_MANY_CONSECUTIVE_REQUESTS
    }

    public record RequestResult(RequestOutcome outcome, Duration retryAfter) {
        static RequestResult of(RequestOutcome outcome, Duration retryAfter) {
            return new RequestResult(outcome, retryAfter);
        }
    }

    public enum FailureReason {
        NONE,
        EXPIRED,
        INVALID
    }

    public record VerifyResult(boolean valid, AppUser user, FailureReason failureReason) {
        public static VerifyResult success(AppUser user) {
            return new VerifyResult(true, user, FailureReason.NONE);
        }

        public static VerifyResult invalid() {
            return new VerifyResult(false, null, FailureReason.INVALID);
        }

        public static VerifyResult expired() {
            return new VerifyResult(false, null, FailureReason.EXPIRED);
        }
    }
}
