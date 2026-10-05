package com.hls.recruitment.internal;

import com.hls.identity.user.AppUser;
import com.hls.identity.user.AppUserRepository;
import com.hls.recruitment.internal.CandidateService.NewCandidate;
import com.hls.recruitment.internal.CollegeService.CollegeRequest;
import com.hls.recruitment.internal.CollegeService.ContactDto;
import com.hls.recruitment.internal.DriveService.DriveRequest;
import com.hls.recruitment.internal.DriveService.StatusRequest;
import com.hls.recruitment.internal.OfferService.OfferRequest;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
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
 * Dev-only demo data (inert unless {@code hls.seed.demo-data=true}, idempotent): one college with a held drive, six
 * candidates, offers in several statuses, and "Ready Rani" whose offer was accepted (the training seeder then enrols
 * and signs her off).
 */
@Component
@Order(50)
@ConditionalOnProperty(name = "hls.seed.demo-data", havingValue = "true")
public class RecruitmentDevSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(RecruitmentDevSeeder.class);
    static final String COLLEGE = "Demo College of Arts";

    private final AppUserRepository users;
    private final CollegeService colleges;
    private final DriveService drives;
    private final CandidateService candidates;
    private final OfferService offers;
    private final Clock clock;

    public RecruitmentDevSeeder(
            AppUserRepository users,
            CollegeService colleges,
            DriveService drives,
            CandidateService candidates,
            OfferService offers,
            Clock clock) {
        this.users = users;
        this.colleges = colleges;
        this.drives = drives;
        this.candidates = candidates;
        this.offers = offers;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            seed();
        } catch (RuntimeException e) {
            log.warn("[DEV SEED] The recruitment demo data could not be seeded: {}", e.getMessage());
        }
    }

    private void seed() {
        AppUser admin = users.findByPhone("9800000001").orElse(null);
        AppUser director = users.findByPhone("9800000002").orElse(null);
        if (admin == null || director == null || !colleges.list(COLLEGE).isEmpty()) {
            return;
        }
        UUID adminId = admin.getId();
        UUID directorId = director.getId();
        Set<com.hls.identity.user.Role> adminRoles = Set.of(com.hls.identity.user.Role.ADMIN);
        LocalDate today = LocalDate.now(clock);
        UUID college = colleges
                .create(adminId, new CollegeRequest(
                        COLLEGE,
                        "Chennai",
                        new ContactDto("Ms. Placement", "9444400001", "placement@demo-college.test"),
                        new ContactDto("Dr. Principal", "9444400002", "principal@demo-college.test"),
                        null))
                .id();
        UUID drive = drives
                .create(adminId, new DriveRequest(college, List.of(today.minusDays(30)), "Main auditorium", "2026-27", List.of(directorId), null))
                .id();
        drives.setStatus(adminId, adminRoles, drive, new StatusRequest("HELD", null, null));

        String[][] people = {
            {"Ready Rani", "9555500001"},
            {"Selected Sana", "9555500002"},
            {"Declined Dev", "9555500003"},
            {"Waitlisted Wasim", "9555500004"},
            {"Rejected Ravi", "9555500005"},
            {"Offered Omar", "9555500006"}
        };
        UUID[] ids = new UUID[people.length];
        for (int i = 0; i < people.length; i++) {
            ids[i] = candidates
                    .add(adminId, adminRoles, drive, new NewCandidate(people[i][0], people[i][1], people[i][0].toLowerCase().replace(' ', '.') + "@demo.test", "B.A. English", "Final", null))
                    .id();
        }
        for (int i : new int[] {0, 1, 2, 5}) {
            candidates.setOutcome(adminId, adminRoles, ids[i], new CandidateService.OutcomeRequest("SELECTED", "Good spoken English"));
        }
        candidates.setOutcome(adminId, adminRoles, ids[3], new CandidateService.OutcomeRequest("WAITLISTED", null));
        candidates.setOutcome(adminId, adminRoles, ids[4], new CandidateService.OutcomeRequest("REJECTED", "Not a fit"));

        OfferRequest terms = new OfferRequest("Trainee / English Trainer", "16000", "Travel allowance 1000", "Two months notice", today.plusDays(10), today.minusDays(20), today.plusDays(30));
        UUID rani = offers.createDraft(directorId, ids[0], terms).id();
        offers.issue(directorId, rani);
        offers.accept(directorId, rani, null);
        UUID declined = offers.createDraft(directorId, ids[2], terms).id();
        offers.issue(directorId, declined);
        offers.decline(directorId, declined, new OfferService.DeclineRequest("Accepted another job"));
        UUID issued = offers.createDraft(directorId, ids[5], terms).id();
        offers.issue(directorId, issued);
        offers.createDraft(directorId, ids[1], terms);
        log.info("[DEV SEED] Recruitment demo data ready: {} with six candidates; Ready Rani has accepted her offer.", COLLEGE);
    }
}
