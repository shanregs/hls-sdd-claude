package com.hls.identity.loginhistory;

import com.hls.identity.user.PhoneNumberNormalizer;
import java.time.Clock;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Publishes every authentication outcome for spec 003's Audit module to record (FR-019,
 * research.md §6). {@code identity} no longer keeps its own copy of login history — the audit
 * module (`com.hls.audit.loginhistory`) is the single append-only store (Constitution
 * Principle VII); this class only masks the phone number and publishes. {@code @Transactional}
 * is kept even though there is no DB write here: {@code @ApplicationModuleListener} fires on
 * transaction commit, and callers of this method (e.g. {@code AuthController}) do not always run
 * inside a transaction of their own — without one here, the event would silently never reach the
 * audit consumer for real (non-test) call paths.
 */
@Service
public class LoginHistoryPublisher {

    private final ApplicationEventPublisher eventPublisher;
    private final PhoneNumberNormalizer phoneNumberNormalizer;
    private final Clock clock;

    public LoginHistoryPublisher(
            ApplicationEventPublisher eventPublisher, PhoneNumberNormalizer phoneNumberNormalizer, Clock clock) {
        this.eventPublisher = eventPublisher;
        this.phoneNumberNormalizer = phoneNumberNormalizer;
        this.clock = clock;
    }

    @Transactional
    public void record(
            UUID userId,
            String phoneNormalized,
            LoginMethod method,
            LoginEventType eventType,
            String outcome,
            String clientIp,
            String deviceDescription) {
        var occurredAt = clock.instant();
        String phoneMasked = phoneNumberNormalizer.mask(phoneNormalized);
        eventPublisher.publishEvent(new LoginHistoryRecorded(
                UUID.randomUUID(), occurredAt, userId, phoneMasked, method, eventType, outcome));
    }
}
