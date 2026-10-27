package com.hls.incentive.internal;

import com.hls.school.api.SchoolDirectory;
import com.hls.school.api.SchoolDirectory.SchoolInfo;
import com.hls.schoolbilling.api.MouApprovalState;
import com.hls.schoolbilling.api.MouApprovals;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Repairs what a missed event would leave undone (spec 030 research D-1, D-9; tasks T014, T032, T019): every School whose
 * MoU counts as approved gets its incentive looked at again, and the hold and payroll links are re-checked in both
 * directions. Every step is idempotent, so running it twice, or while an event is being handled, changes nothing more.
 * Daily at 03:15 unless the property below is false.
 */
@Component
public class ReconcileJob {

    private static final Logger log = LoggerFactory.getLogger(ReconcileJob.class);

    /** What one run did, for tests and the log. */
    public record Result(int looked, int earned, int failed, int held, int released, int offered) {}

    private final SchoolDirectory schools;
    private final MouApprovals mou;
    private final EarningService earning;
    private final HoldService holds;
    private final IncentiveRepository incentives;
    private final Clock clock;
    private final boolean enabled;

    ReconcileJob(
            SchoolDirectory schools,
            MouApprovals mou,
            EarningService earning,
            HoldService holds,
            IncentiveRepository incentives,
            Clock clock,
            @Value("${hls.incentive.reconcile.enabled:true}") boolean enabled) {
        this.schools = schools;
        this.mou = mou;
        this.earning = earning;
        this.holds = holds;
        this.incentives = incentives;
        this.clock = clock;
        this.enabled = enabled;
    }

    @Scheduled(cron = "0 15 3 * * *")
    void scheduled() {
        if (enabled) {
            Result result = runNow();
            log.info("Incentive reconcile: looked at {} Schools, earned {}, held {}, released {}, offered {}, failed {}", result.looked(),
                    result.earned(), result.held(), result.released(), result.offered(), result.failed());
        }
    }

    /** Runs the whole reconcile regardless of the schedule switch (tests, operators). */
    public Result runNow() {
        LocalDate today = LocalDate.now(clock.withZone(IncentiveRecorder.BUSINESS_ZONE));
        List<UUID> ids = schools.allSchools().stream().map(SchoolInfo::id).toList();
        Map<UUID, MouApprovalState> states = mou.stateOfAll(ids, today);
        int looked = 0;
        int earned = 0;
        int failed = 0;
        for (UUID id : ids) {
            MouApprovalState state = states.get(id);
            if (state == null || !state.countsAsApproved()) {
                continue;
            }
            looked++;
            try {
                if (earning.earn(id) == EarningService.Outcome.EARNED) {
                    earned++;
                }
            } catch (RuntimeException e) {
                failed++;
                log.warn("Incentive reconcile failed for School {}", id, e);
            }
        }
        int held = 0;
        int released = 0;
        int offered = 0;
        // both directions: approved incentives whose MoU stopped counting, held ones whose MoU counts again, lost offers
        for (Incentive incentive : incentives.findAll()) {
            if (incentive.getStatus() != IncentiveStatus.APPROVED && incentive.getStatus() != IncentiveStatus.HELD) {
                continue;
            }
            try {
                switch (holds.reevaluate(incentive.getSchoolId())) {
                    case HELD -> held++;
                    case RELEASED -> released++;
                    case RETRIED_OFFER -> offered++;
                    default -> {
                        // nothing to repair
                    }
                }
            } catch (RuntimeException e) {
                failed++;
                log.warn("Incentive reconcile could not check incentive {}", incentive.getId(), e);
            }
        }
        return new Result(looked, earned, failed, held, released, offered);
    }
}
