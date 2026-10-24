package com.hls.transfer.internal;

import com.hls.school.api.ChangeRecorder;
import com.hls.transfer.api.TransferApplied;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The small daily job of spec 027: sends the "took effect" notice once for an Approved request whose date has arrived, and
 * closes a request whose placement row was cancelled by another route. Correctness never depends on it: the shown status
 * (Applied, Cancelled) is derived from the dates and rows on every read.
 */
@Component
public class MovementEffectJob {

    private static final Logger log = LoggerFactory.getLogger(MovementEffectJob.class);

    /** Changes made by the job itself are attributed to this id in Change History. */
    private static final UUID SYSTEM_ACTOR = new UUID(0L, 0L);

    /** What one run did. */
    public record Result(int applied, int closedStale) {}

    private final MovementRequestRepository requests;
    private final MovementParticipantRepository participants;
    private final MovementHistoryRepository history;
    private final MovementQueries queries;
    private final ChangeRecorder changes;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final boolean enabled;
    private final TransactionTemplate tx;

    MovementEffectJob(
            MovementRequestRepository requests,
            MovementParticipantRepository participants,
            MovementHistoryRepository history,
            MovementQueries queries,
            ChangeRecorder changes,
            ApplicationEventPublisher events,
            Clock clock,
            PlatformTransactionManager transactions,
            @Value("${hls.transfer.effect-job.enabled:true}") boolean enabled) {
        this.requests = requests;
        this.participants = participants;
        this.history = history;
        this.queries = queries;
        this.changes = changes;
        this.events = events;
        this.clock = clock;
        this.enabled = enabled;
        this.tx = new TransactionTemplate(transactions);
    }

    @Scheduled(cron = "0 20 6 * * *", zone = "${hls.business-timezone:Asia/Kolkata}")
    void scheduled() {
        if (enabled) {
            Result result = runNow();
            log.info("Transfers: {} took effect, {} closed as stale", result.applied(), result.closedStale());
        }
    }

    /** Runs the job now (also used by the tests); one request per transaction so one failure does not stop the rest. */
    public Result runNow() {
        int applied = 0;
        int stale = 0;
        List<UUID> approved =
                tx.execute(s -> requests.findByStatus("APPROVED").stream().map(MovementRequest::getId).toList());
        for (UUID id : approved) {
            try {
                String outcome = tx.execute(s -> process(id));
                if ("APPLIED".equals(outcome)) {
                    applied++;
                } else if ("STALE".equals(outcome)) {
                    stale++;
                }
            } catch (RuntimeException e) {
                log.warn("Transfer request {} could not be processed: {}", id, e.getMessage());
            }
        }
        return new Result(applied, stale);
    }

    private String process(UUID id) {
        MovementRequest r = requests.findById(id).orElse(null);
        if (r == null || !"APPROVED".equals(r.getStatus())) {
            return "SKIP";
        }
        MovementQueries.Shown shown = queries.shown(r);
        if ("CANCELLED".equals(shown.status())) {
            r.decide("CANCELLED", r.getDecidedBy(), shown.note(), clock.instant());
            List<MovementParticipant> open = participants.findByRequestId(id);
            open.forEach(MovementParticipant::close);
            participants.saveAll(open);
            requests.saveAndFlush(r);
            history.save(new MovementRequestHistory(id, "CLOSED_STALE", "APPROVED", "CANCELLED", null, shown.note(), clock.instant()));
            changes.record(SYSTEM_ACTOR, MovementService.ENTITY, id, "status", "APPROVED", "CANCELLED");
            return "STALE";
        }
        if ("APPLIED".equals(shown.status()) && r.getAppliedNotifiedAt() == null) {
            r.setAppliedNotifiedAt(clock.instant());
            requests.saveAndFlush(r);
            history.save(new MovementRequestHistory(id, "APPLIED", "APPROVED", "APPROVED", null, null, clock.instant()));
            events.publishEvent(new TransferApplied(
                    id, r.getKind(), r.getTeacherId(), r.getReplacementTeacherId(), r.getFromSchoolId(), r.getToSchoolId(), r.getEffectiveOn()));
            return "APPLIED";
        }
        return "SKIP";
    }
}
