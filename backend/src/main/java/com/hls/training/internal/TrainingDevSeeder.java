package com.hls.training.internal;

import com.hls.identity.user.AppUser;
import com.hls.identity.user.AppUserRepository;
import com.hls.training.internal.BatchService.BatchRequest;
import com.hls.training.internal.BatchService.RecruitRef;
import com.hls.training.internal.SignOffService.SignOffRequest;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Dev-only demo data (inert unless {@code hls.seed.demo-data=true}, idempotent): a running induction batch that takes
 * the recruits waiting to be enrolled, with "Ready Rani" signed off as completed so she appears as ready to deploy.
 */
@Component
@Order(60)
@ConditionalOnProperty(name = "hls.seed.demo-data", havingValue = "true")
public class TrainingDevSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(TrainingDevSeeder.class);
    static final String BATCH = "Demo Induction";

    private final AppUserRepository users;
    private final BatchService batches;
    private final SignOffService signOffs;
    private final InductionEnrolmentRepository enrolments;
    private final Clock clock;

    public TrainingDevSeeder(
            AppUserRepository users,
            BatchService batches,
            SignOffService signOffs,
            InductionEnrolmentRepository enrolments,
            Clock clock) {
        this.users = users;
        this.batches = batches;
        this.signOffs = signOffs;
        this.enrolments = enrolments;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            seed();
        } catch (RuntimeException e) {
            log.warn("[DEV SEED] The induction demo data could not be seeded: {}", e.getMessage());
        }
    }

    private void seed() {
        AppUser director = users.findByPhone("9800000002").orElse(null);
        if (director == null || batches.list().stream().anyMatch(b -> BATCH.equals(b.name()))) {
            return;
        }
        List<RecruitRef> waiting = batches.toBeEnrolled();
        if (waiting.stream().noneMatch(r -> r.name().equals("Ready Rani"))) {
            return;
        }
        LocalDate today = LocalDate.now(clock);
        UUID batch = batches
                .create(director.getId(), new BatchRequest(BATCH, today.minusDays(20), today.plusDays(10), "Trainer Tina", "PHYSICAL", "Head office", 10))
                .id();
        for (RecruitRef recruit : waiting) {
            InductionEnrolment enrolment = batches.enrol(director.getId(), batch, recruit.teacherId());
            if (recruit.name().equals("Ready Rani")) {
                signOffs.signOff(director.getId(), enrolment.getId(), new SignOffRequest("COMPLETED", "Completed the induction"));
            }
        }
        log.info("[DEV SEED] Induction demo data ready: {} with Ready Rani signed off.", BATCH);
    }
}
