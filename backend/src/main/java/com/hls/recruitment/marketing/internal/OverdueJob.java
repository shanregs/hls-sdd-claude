package com.hls.recruitment.marketing.internal;

import com.hls.recruitment.api.WonProspectOverdue;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Daily check for won prospects whose MoU is still not recorded after the allowed days. Each prospect is reported once
 * per limit (a row in {@code overdue_notice}); it is reported again only if the limit is changed and passed again.
 * The flag on the board is read from the same rule, so it shows even with the job switched off.
 */
@Component
public class OverdueJob {

    private static final Logger log = LoggerFactory.getLogger(OverdueJob.class);

    private final ProspectRepository prospects;
    private final OverdueNoticeRepository notices;
    private final EffectiveStageResolver stages;
    private final SettingsService settings;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final boolean enabled;
    private final org.springframework.transaction.support.TransactionTemplate tx;

    OverdueJob(
            ProspectRepository prospects,
            OverdueNoticeRepository notices,
            EffectiveStageResolver stages,
            SettingsService settings,
            ApplicationEventPublisher events,
            Clock clock,
            org.springframework.transaction.PlatformTransactionManager transactions,
            @Value("${hls.marketing.overdue.enabled:true}") boolean enabled) {
        this.tx = new org.springframework.transaction.support.TransactionTemplate(transactions);
        this.prospects = prospects;
        this.notices = notices;
        this.stages = stages;
        this.settings = settings;
        this.events = events;
        this.clock = clock;
        this.enabled = enabled;
    }

    @Scheduled(cron = "0 0 3 * * *")
    void scheduled() {
        if (enabled) {
            Integer reported = tx.execute(status -> runNow());
            log.info("Reported {} won prospects with no MoU recorded", reported);
        }
    }

    /** Reports the prospects that are newly overdue; returns how many. */
    @Transactional
    public int runNow() {
        int days = settings.overdueDays();
        Instant limit = clock.instant().minus(days, ChronoUnit.DAYS);
        List<Prospect> won = prospects.findByWonAtIsNotNull();
        Map<UUID, String> effective = stages.of(won);
        int reported = 0;
        for (Prospect p : won) {
            boolean overdue = EffectiveStageResolver.WON.equals(effective.get(p.getId())) && !p.getWonAt().isAfter(limit);
            if (overdue && !notices.existsByProspectIdAndLimitDays(p.getId(), days)) {
                notices.save(new OverdueNotice(p.getId(), days, clock.instant()));
                events.publishEvent(new WonProspectOverdue(p.getId(), p.getName(), p.getOwnerUserId(), p.getZoneId(), days));
                reported++;
            }
        }
        return reported;
    }
}
