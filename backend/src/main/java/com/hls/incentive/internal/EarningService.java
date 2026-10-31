package com.hls.incentive.internal;

import com.hls.identity.user.AppUser;
import com.hls.identity.user.AppUserRepository;
import com.hls.incentive.api.IncentiveEarned;
import com.hls.incentive.api.IncentiveSettings;
import com.hls.organization.api.ManagerQueries;
import com.hls.organization.api.ManagerQueries.ManagerRef;
import com.hls.recruitment.api.ProspectOwners;
import com.hls.school.api.SchoolDirectory;
import com.hls.school.api.SchoolDirectory.SchoolInfo;
import com.hls.schoolbilling.api.MouApprovalState;
import com.hls.schoolbilling.api.MouApprovals;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Decides whether a School's approved MoU earns a Manager an incentive and creates it (spec 030 US1, research D-1 to D-6).
 * Idempotent per School: the unique {@code school_id} and a per-School advisory lock mean a repeated event, the daily job
 * and a re-approval can never make a second one. Approval of the MoU itself is never touched: this runs after it.
 */
@Service
public class EarningService {

    private static final Logger log = LoggerFactory.getLogger(EarningService.class);

    /** What one look at a School found. */
    public enum Outcome {
        EARNED,
        ALREADY_EARNED,
        NOT_APPROVED,
        NO_EARNER,
        NOTHING_TO_PAY
    }

    private final IncentiveRepository incentives;
    private final NoEarnerRepository noEarners;
    private final IncentiveProgramRepository program;
    private final IncentiveRecorder recorder;
    private final MouApprovals mou;
    private final ProspectOwners owners;
    private final ManagerQueries managers;
    private final SchoolDirectory schools;
    private final IncentiveSettings settings;
    private final AppUserRepository users;
    private final ApplicationEventPublisher publisher;
    private final EntityManager entityManager;
    private final Clock clock;

    EarningService(
            IncentiveRepository incentives,
            NoEarnerRepository noEarners,
            IncentiveProgramRepository program,
            IncentiveRecorder recorder,
            MouApprovals mou,
            ProspectOwners owners,
            ManagerQueries managers,
            SchoolDirectory schools,
            IncentiveSettings settings,
            AppUserRepository users,
            ApplicationEventPublisher publisher,
            EntityManager entityManager,
            Clock clock) {
        this.incentives = incentives;
        this.noEarners = noEarners;
        this.program = program;
        this.recorder = recorder;
        this.mou = mou;
        this.owners = owners;
        this.managers = managers;
        this.schools = schools;
        this.settings = settings;
        this.users = users;
        this.publisher = publisher;
        this.entityManager = entityManager;
        this.clock = clock;
    }

    /** Looks at one School and creates its incentive when it earns one. Safe to call any number of times. */
    @Transactional
    public Outcome earn(UUID schoolId) {
        entityManager
                .createNativeQuery("select pg_advisory_xact_lock(hashtextextended(?1, 0))")
                .setParameter(1, "incentive:" + schoolId)
                .getSingleResult();
        Instant now = clock.instant();
        Optional<Incentive> existing = incentives.findBySchoolId(schoolId);
        if (existing.isPresent()) {
            noEarners.findBySchoolId(schoolId).ifPresent(row -> row.resolve(now));
            return Outcome.ALREADY_EARNED;
        }
        LocalDate today = LocalDate.now(clock.withZone(IncentiveRecorder.BUSINESS_ZONE));
        MouApprovalState state = mou.stateOn(schoolId, today);
        if (!state.countsAsApproved()) {
            return Outcome.NOT_APPROVED;
        }
        if (state.approvedOn() == null) {
            return noEarner(schoolId, state, NoEarnerReason.APPROVED_BEFORE_APPROVAL_STEP, "Approved before the approval step", now);
        }
        Instant startsAt = program.findById((short) 1).map(IncentiveProgram::getStartsAt).orElse(Instant.EPOCH);
        if (state.approvedOn().isBefore(startsAt)) {
            return noEarner(schoolId, state, NoEarnerReason.BEFORE_GO_LIVE, "Approved before incentives began", now);
        }
        Optional<UUID> owner = owners.ownerOfSchool(schoolId);
        if (owner.isEmpty()) {
            return noEarner(schoolId, state, NoEarnerReason.NO_PROSPECT, "No won prospect is linked to this School", now);
        }
        Optional<ManagerRef> manager = managers.managerByUserId(owner.get());
        if (manager.isEmpty()) {
            return noEarner(schoolId, state, NoEarnerReason.OWNER_NOT_MANAGER, "Owner: " + nameOf(owner.get()), now);
        }
        if (!manager.get().active()) {
            return noEarner(schoolId, state, NoEarnerReason.NO_OWNER, "Owner has left: " + manager.get().displayName(), now);
        }
        int rupees = settings.amountPerSchool();
        if (rupees <= 0) {
            log.info("Incentive amount is zero, no incentive created for School {}", schoolId);
            return Outcome.NOTHING_TO_PAY;
        }
        SchoolInfo school = schools.school(schoolId).orElse(null);
        if (school == null) {
            return Outcome.NOT_APPROVED;
        }
        LocalDate closure = state.approvedOn().atZone(IncentiveRecorder.BUSINESS_ZONE).toLocalDate();
        Incentive incentive = incentives.save(new Incentive(
                schoolId,
                state.contractId(),
                manager.get().id(),
                owner.get(),
                BigDecimal.valueOf(rupees).setScale(2),
                closure,
                school.zoneId(),
                now));
        recorder.record(
                null,
                incentive.getId(),
                "EARNED",
                "Earned by " + manager.get().displayName() + " for " + school.name() + ", Rs " + incentive.getAmount());
        noEarners.findBySchoolId(schoolId).ifPresent(row -> row.resolve(now));
        UUID zoneManager = managers.managerOfSchool(schoolId)
                .filter(ManagerRef::active)
                .map(ManagerRef::userId)
                .orElseGet(() -> managers.managersOfZone(school.zoneId()).stream()
                        .map(ManagerRef::userId)
                        .filter(java.util.Objects::nonNull)
                        .findFirst()
                        .orElse(null));
        publisher.publishEvent(new IncentiveEarned(incentive.getId(), schoolId, owner.get(), zoneManager));
        return Outcome.EARNED;
    }

    private Outcome noEarner(UUID schoolId, MouApprovalState state, NoEarnerReason reason, String detail, Instant now) {
        NoEarnerRecord row = noEarners.findBySchoolId(schoolId).orElse(null);
        if (row == null) {
            noEarners.save(new NoEarnerRecord(schoolId, state.contractId(), reason, detail, now));
        } else {
            row.update(state.contractId(), reason, detail);
        }
        return Outcome.NO_EARNER;
    }

    private String nameOf(UUID userId) {
        return users.findById(userId).map(AppUser::getDisplayName).orElse("unknown user");
    }
}
