package com.hls.schoolbilling.internal;

import com.hls.school.api.ChangeRecorder;
import com.hls.school.api.InvalidInputException;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** The two billing settings (spec 022 research R10): payment due days and escalation days, audited on every change. */
@Component
public class BillingSettings {

    public static final String PAYMENT_DUE_DAYS = "billing.payment_due_days";
    public static final String ESCALATION_DAYS = "billing.escalation_days";
    static final int DEFAULT_PAYMENT_DUE_DAYS = 15;
    static final int DEFAULT_ESCALATION_DAYS = 7;
    static final String ENTITY = "BILLING_SETTING";

    private final JdbcTemplate jdbc;
    private final ChangeRecorder changes;
    private final Clock clock;

    BillingSettings(JdbcTemplate jdbc, ChangeRecorder changes, Clock clock) {
        this.jdbc = jdbc;
        this.changes = changes;
        this.clock = clock;
    }

    public int paymentDueDays() {
        return read(PAYMENT_DUE_DAYS, DEFAULT_PAYMENT_DUE_DAYS);
    }

    public int escalationDays() {
        return read(ESCALATION_DAYS, DEFAULT_ESCALATION_DAYS);
    }

    @Transactional
    public void update(UUID actor, Integer paymentDueDays, Integer escalationDays) {
        int due = paymentDueDays == null ? paymentDueDays() : paymentDueDays;
        int escalate = escalationDays == null ? escalationDays() : escalationDays;
        if (due < 1 || due > 120) {
            throw new InvalidInputException("Payment due days must be between 1 and 120.");
        }
        if (escalate < 2 || escalate > 120) {
            throw new InvalidInputException("Escalation days must be between 2 and 120.");
        }
        write(actor, PAYMENT_DUE_DAYS, paymentDueDays(), due);
        write(actor, ESCALATION_DAYS, escalationDays(), escalate);
    }

    private void write(UUID actor, String key, int before, int after) {
        jdbc.update(
                """
                insert into billing_setting (key, value, updated_by, updated_at) values (?, ?, ?, ?)
                on conflict (key) do update set value = excluded.value, updated_by = excluded.updated_by,
                    updated_at = excluded.updated_at
                """,
                key,
                after,
                actor,
                Timestamp.from(clock.instant()));
        changes.record(actor, ENTITY, key, "value", before, after);
    }

    private int read(String key, int fallback) {
        List<Integer> values = jdbc.queryForList("select value from billing_setting where key = ?", Integer.class, key);
        return values.isEmpty() ? fallback : values.get(0);
    }
}
