package com.hls.recruitment.marketing;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import com.hls.recruitment.api.ProspectWon;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;

/** Winning a prospect: only Admin and Director create its School, only after approval, with a hand-off to spec 012. */
@Import(WinServiceTest.Recorder.class)
class WinServiceTest extends WonProspectTestBase {

    @TestConfiguration
    static class Recorder {
        final List<ProspectWon> seen = new CopyOnWriteArrayList<>();

        @EventListener
        void on(ProspectWon event) {
            seen.add(event);
        }
    }

    @Autowired
    Recorder events;

    @Test
    void anApprovedProspectGetsItsSchoolCreatedFromItsDetailsWithAHandOffToTheMouForm() {
        String admin = admin();
        Won won = wonProspect(admin);
        UUID place = place(admin, won.zone());
        Map<String, Object> body = winBody(place);
        body.put("principal", Map.of("name", "Mrs Rao", "phone", "9111111111"));
        body.put("accountant", Map.of("name", "Mr Iyer", "email", "iyer@school.test"));

        Resp resp = post(M + "/prospects/" + won.prospect() + "/win", admin, body);

        assertThat(resp.status()).as(resp.body()).isEqualTo(200);
        UUID school = schoolOf(admin, won.prospect());
        assertThat(resp.body()).contains("/operations/school-contracts/schools/" + school, "Proposal (not a contract)", "\"available\":true");
        Resp schoolView = get("/api/v1/schools/" + school, admin);
        assertThat(schoolView.body()).contains("5 Lake Road", "accounts@school.test", "Mrs Rao");
        assertThat(get("/api/v1/schools/" + school + "/contacts", admin).body()).contains("Mrs Rao", "Mr Iyer", "iyer@school.test");
        assertThat(events.seen).anyMatch(e -> e.prospectId().equals(won.prospect()) && e.schoolId().equals(school));
        assertChangeRecorded(admin, "PROSPECT_SCHOOL", won.prospect(), "school");
        assertThat(post(M + "/prospects/" + won.prospect() + "/win", admin, winBody(place)).status()).isEqualTo(409);
    }

    @Test
    void aZoneManagerCannotCreateTheSchoolAndNothingIsCreatedBeforeApproval() {
        String admin = admin();
        UUID zone = zone(admin);
        ManagerCtx manager = newManager(admin, zone);
        UUID place = place(admin, zone);
        UUID unapproved = prospect(admin, zone);
        Won won = wonProspect(admin);
        UUID wonPlace = place(admin, won.zone());

        assertThat(post(M + "/prospects/" + unapproved + "/win", admin, winBody(place)).status()).isEqualTo(409);
        assertThat(post(M + "/prospects/" + won.prospect() + "/win", manager.token(), winBody(wonPlace)).status()).isIn(403, 404);
        for (Role role : List.of(Role.TEACHER, Role.SYSTEM)) {
            assertThat(post(M + "/prospects/" + won.prospect() + "/win", signInAs(role).token(), winBody(wonPlace)).status()).isEqualTo(403);
        }
        assertThat(jdbc.queryForObject("select school_id from marketing_prospect where id = ?", UUID.class, won.prospect())).isNull();
    }

    @Test
    void aZoneManagerOfTheZoneIsStillRefusedBecauseOnlyAdminAndDirectorCreateSchools() {
        String admin = admin();
        Won won = wonProspect(admin);
        ManagerCtx manager = newManager(admin, won.zone());
        UUID place = place(admin, won.zone());

        assertThat(post(M + "/prospects/" + won.prospect() + "/win", manager.token(), winBody(place)).status()).isEqualTo(403);
    }

    @Test
    void thePlaceMustBeInTheProspectsZoneAndTheBillingContactIsRequired() {
        String admin = admin();
        Won won = wonProspect(admin);
        UUID otherZonePlace = place(admin, zone(admin));
        UUID place = place(admin, won.zone());
        Map<String, Object> noBilling = winBody(place);
        noBilling.remove("billingContact");

        assertThat(post(M + "/prospects/" + won.prospect() + "/win", admin, winBody(otherZonePlace)).status()).isEqualTo(400);
        assertThat(post(M + "/prospects/" + won.prospect() + "/win", admin, noBilling).status()).isEqualTo(400);
        assertThat(post(M + "/prospects/" + won.prospect() + "/win", admin, Map.of("billingContact", "x")).status()).isEqualTo(400);
    }

    @Test
    void anExistingSchoolOfTheSameNameAndPlaceIsOfferedForLinkingNeverDuplicated() {
        String admin = admin();
        Won won = wonProspect(admin);
        UUID place = place(admin, won.zone());
        String name = jdbc.queryForObject("select name from marketing_prospect where id = ?", String.class, won.prospect());
        Resp school = post("/api/v1/schools", admin, Map.of("name", name, "placeId", place, "address", "9 Old Road"));
        assertThat(school.status()).as(school.body()).isEqualTo(201);

        Resp refused = post(M + "/prospects/" + won.prospect() + "/win", admin, winBody(place));
        assertThat(refused.status()).isEqualTo(409);
        assertThat(refused.body()).contains("link it instead", school.id().toString());

        Map<String, Object> link = new java.util.HashMap<>();
        link.put("placeId", place);
        link.put("linkSchoolId", school.id());
        Resp linked = post(M + "/prospects/" + won.prospect() + "/win", admin, link);
        assertThat(linked.status()).as(linked.body()).isEqualTo(200);
        assertThat(schoolOf(admin, won.prospect())).isEqualTo(school.id());
        Integer same = jdbc.queryForObject("select count(*) from school where lower(name) = lower(?) and place_id = ?", Integer.class, name, place);
        assertThat(same).isEqualTo(1);

        // a second prospect cannot be linked to the same School
        Won other = wonProspect(admin);
        Map<String, Object> again = new java.util.HashMap<>();
        again.put("placeId", place(admin, other.zone()));
        again.put("linkSchoolId", school.id());
        assertThat(post(M + "/prospects/" + other.prospect() + "/win", admin, again).status()).isIn(400, 409);
    }
}
