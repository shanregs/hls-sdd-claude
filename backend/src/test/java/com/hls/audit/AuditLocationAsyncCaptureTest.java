package com.hls.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.auth.AuthDtos;
import com.hls.identity.clientcontext.ClientContext;
import com.hls.identity.clientcontext.ClientSource;
import com.hls.identity.clientcontext.LocationCapture;
import com.hls.identity.loginhistory.LoginEventType;
import com.hls.identity.loginhistory.LoginHistoryRecorded;
import com.hls.identity.loginhistory.LoginMethod;
import com.hls.identity.user.Role;
import com.hls.support.MobileTestBase;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Spec 018 T064: the client context is captured when the event is published (in the request
 * thread), so an audit row consumed afterwards still carries that request's location, and a
 * redelivered event creates no duplicate row.
 */
class AuditLocationAsyncCaptureTest extends MobileTestBase {

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void theRowKeepsTheLocationOfItsOwnRequestEvenWhenLaterRequestsCarryOthers() {
        String phone = nextPhone();
        UUID id = userAdminService
                .createUser("Async Teacher " + phone, phone, Set.of(Role.TEACHER), null, PASSWORD)
                .getId();

        Raw login = postRaw(
                "/api/v1/auth/login",
                new AuthDtos.LoginRequest(phone, PASSWORD),
                ANDROID,
                withLocation(10.0, 20.0, 5.0));
        // Immediately fire more requests that carry different locations while the consumer runs.
        for (int i = 0; i < 6; i++) {
            getRaw("/api/v1/me/profile", login.string("accessToken"), ANDROID, withLocation(50.0 + i, 60.0 + i, 5.0));
        }

        List<Map<String, Object>> rows = awaitRows(
                "SELECT * FROM login_history_entry WHERE user_id = ? AND event_type = 'SIGN_IN_SUCCESS'", 1, id);
        assertThat(rows).hasSize(1);
        assertThat(((Number) rows.get(0).get("latitude")).doubleValue()).isEqualTo(10.0);
        assertThat(((Number) rows.get(0).get("longitude")).doubleValue()).isEqualTo(20.0);
    }

    @Test
    void aRedeliveredEventCreatesNoDuplicateRow() {
        UUID eventId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        ClientContext context = new ClientContext(
                ClientSource.ANDROID, "1.0.0", LocationCapture.available(1.5, 2.5, 3.0, Instant.now()), false);
        LoginHistoryRecorded event = new LoginHistoryRecorded(
                eventId,
                Instant.now(),
                userId,
                "******1234",
                LoginMethod.PASSWORD,
                LoginEventType.SIGN_IN_SUCCESS,
                "Signed in",
                context);
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        tx.executeWithoutResult(status -> eventPublisher.publishEvent(event));
        awaitRows("SELECT * FROM login_history_entry WHERE source_event_id = ?", 1, eventId);
        tx.executeWithoutResult(status -> eventPublisher.publishEvent(event));
        sleepBriefly();

        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM login_history_entry WHERE source_event_id = ?", Integer.class, eventId);
        assertThat(count).isEqualTo(1);
    }

    private static void sleepBriefly() {
        try {
            Thread.sleep(1500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
