package com.hls.identity.internal;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * FR-009: rate-limits OTP requests per phone number. In-memory (research.md §4) —
 * sufficient at the constitution's single-EC2, &lt;100-user scale; revisit only if
 * a second app instance is ever introduced.
 */
@Component
public class OtpRateLimiter {

    private final ConcurrentMap<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final IdentityProperties properties;

    public OtpRateLimiter(IdentityProperties properties) {
        this.properties = properties;
    }

    /** @return true if this request is allowed; false if the phone number is currently rate-limited. */
    public boolean tryConsume(String phoneNumber) {
        Bucket bucket = buckets.computeIfAbsent(phoneNumber, key -> Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(properties.getOtp().getRateLimitPerMinute())
                        .refillGreedy(properties.getOtp().getRateLimitPerMinute(), Duration.ofMinutes(1))
                        .build())
                .build());
        return bucket.tryConsume(1);
    }
}
