package com.hls.recruitment.marketing.internal;

import com.hls.identity.user.AppUser;
import com.hls.identity.user.AppUserRepository;
import com.hls.identity.user.Role;
import com.hls.school.api.SchoolDirectory;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Dev-only demo data (inert unless {@code hls.seed.demo-data=true}, idempotent): prospects of the Demo Zone at several
 * stages, one with a completed visit whose follow-up is overdue, one in Final Stage waiting for review, and a won
 * prospect linked to Demo School One, which already has an MoU from the contract seeder.
 */
@Component
@Order(70)
@ConditionalOnProperty(name = "hls.seed.demo-data", havingValue = "true")
public class MarketingDevSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MarketingDevSeeder.class);
    static final String MARKER = "Green Valley Public School";

    private final AppUserRepository users;
    private final SchoolDirectory schools;
    private final ProspectService prospects;
    private final ProposalService proposals;
    private final PipelineService pipeline;
    private final ActivityService activities;
    private final WinService wins;
    private final Clock clock;

    public MarketingDevSeeder(
            AppUserRepository users,
            SchoolDirectory schools,
            ProspectService prospects,
            ProposalService proposals,
            PipelineService pipeline,
            ActivityService activities,
            WinService wins,
            Clock clock) {
        this.users = users;
        this.schools = schools;
        this.prospects = prospects;
        this.proposals = proposals;
        this.pipeline = pipeline;
        this.activities = activities;
        this.wins = wins;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            seed();
        } catch (RuntimeException e) {
            log.warn("[DEV SEED] The marketing demo data could not be seeded: {}", e.getMessage());
        }
    }

    private void seed() {
        AppUser admin = users.findByPhone("9800000001").orElse(null);
        AppUser director = users.findByPhone("9800000002").orElse(null);
        Optional<SchoolDirectory.ZoneInfo> zone = schools.zoneByName("Demo Zone");
        if (admin == null || director == null || zone.isEmpty()) {
            return;
        }
        Set<Role> adminRoles = Set.of(Role.ADMIN);
        Set<Role> directorRoles = Set.of(Role.DIRECTOR);
        if (prospects.list(admin.getId(), adminRoles, null, null, null, MARKER, 0, 5).totalElements() > 0) {
            return;
        }
        UUID zoneId = zone.get().id();
        LocalDate today = LocalDate.now(clock);

        UUID contacted = create(admin, adminRoles, zoneId, MARKER, "CBSE", "Mrs Rao", 4);
        pipeline.move(admin.getId(), adminRoles, contacted, new PipelineService.StageRequest("CONTACTED", null, null));

        UUID visited = create(admin, adminRoles, zoneId, "Sunrise Matriculation School", "State Board", "Mr Kumar", 6);
        pipeline.move(admin.getId(), adminRoles, visited, new PipelineService.StageRequest("VISIT", null, null));
        var visit = activities.plan(
                admin.getId(), adminRoles, new ActivityService.PlanRequest(visited, null, "VISIT", today.minusDays(12), List.of(admin.getId()), "First visit"));
        activities.complete(
                admin.getId(),
                adminRoles,
                visit.id(),
                new ActivityService.CompleteRequest("Met the principal. Interested in 6 Teachers; wants a proposal.", null, today.minusDays(3), null));

        UUID negotiating = create(admin, adminRoles, zoneId, "Lakeview International School", "ICSE", "Ms Fernandes", 3);
        proposals.create(admin.getId(), adminRoles, negotiating, proposal(3, "16000"));
        pipeline.move(admin.getId(), adminRoles, negotiating, new PipelineService.StageRequest("NEGOTIATION", null, null));
        activities.plan(
                admin.getId(), adminRoles, new ActivityService.PlanRequest(negotiating, null, "PROPOSAL_MEETING", today.plusDays(4), List.of(admin.getId()), "Review the proposal"));

        UUID finalStage = create(admin, adminRoles, zoneId, "Hilltop Public School", "CBSE", "Dr Menon", 5);
        proposals.create(admin.getId(), adminRoles, finalStage, proposal(5, "15500"));
        pipeline.move(admin.getId(), adminRoles, finalStage, new PipelineService.StageRequest("FINAL_STAGE", null, null));

        UUID won = create(admin, adminRoles, zoneId, "Demo School One", "CBSE", "R. Kumar", 4);
        proposals.create(admin.getId(), adminRoles, won, proposal(4, "15000"));
        pipeline.move(admin.getId(), adminRoles, won, new PipelineService.StageRequest("FINAL_STAGE", null, null));
        pipeline.review(director.getId(), directorRoles, won, new PipelineService.ReviewRequest("APPROVE", null));
        schools.schoolsInZone(zoneId).stream().filter(s -> s.name().equals("Demo School One")).findFirst().ifPresent(school -> {
            wins.win(
                    admin.getId(),
                    adminRoles,
                    won,
                    new WinService.WinRequest(school.placeId(), null, school.id(), null, null));
        });
        log.info("[DEV SEED] Marketing demo data ready: five prospects in Demo Zone, one of them won and linked to Demo School One.");
    }

    private UUID create(AppUser actor, Set<Role> roles, UUID zone, String name, String board, String contact, int teachers) {
        return prospects
                .create(actor.getId(), roles, new ProspectService.ProspectRequest(
                        name, board, "12 Demo Road, Chennai", zone, contact, "Principal", "9444400100", null, teachers, null, null))
                .row()
                .id();
    }

    private ProposalService.ProposalRequest proposal(int teachers, String rate) {
        return new ProposalService.ProposalRequest(teachers, LocalDate.now(clock).plusMonths(1).toString().substring(0, 7), "SAME_FOR_ALL", rate, null, "Demo proposal");
    }
}
