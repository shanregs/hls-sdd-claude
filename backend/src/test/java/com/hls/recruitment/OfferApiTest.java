package com.hls.recruitment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hls.identity.user.Role;
import com.hls.teacher.api.TeacherRegistry;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Offers: the rules (one open offer, never edited once issued), the role matrix, accepting, and the letter. */
class OfferApiTest extends RecruitmentTestBase {

    @Autowired
    TeacherRegistry registry;

    @Test
    void onlyASelectedCandidateCanBeOfferedAndASecondOpenOfferIsRefused() {
        String admin = admin();
        String director = directorToken();
        UUID drive = drive(admin);
        UUID waitlisted = candidate(admin, drive);
        assertThat(outcome(admin, waitlisted, "WAITLISTED").status()).isEqualTo(200);
        assertThat(post(BASE + "/candidates/" + waitlisted + "/offers", director, offerBody("15000")).status()).isEqualTo(409);

        UUID selected = selected(admin, phone());
        Resp first = post(BASE + "/candidates/" + selected + "/offers", director, offerBody("15000"));
        assertThat(first.status()).as(first.body()).isEqualTo(201);
        assertThat(first.map()).containsEntry("status", "DRAFT").containsEntry("monthlySalary", "15000.00");
        assertThat(post(BASE + "/candidates/" + selected + "/offers", director, offerBody("16000")).status()).isEqualTo(409);
        // the outcome cannot be changed under an open offer
        assertThat(outcome(admin, selected, "REJECTED").status()).isEqualTo(409);
    }

    @Test
    void theSamePersonSelectedAtTwoDrivesStillGetsOnlyOneOpenOffer() {
        String admin = admin();
        String director = directorToken();
        String phone = phone();
        UUID one = selected(admin, phone);
        UUID two = selected(admin, phone);

        assertThat(post(BASE + "/candidates/" + one + "/offers", director, offerBody("15000")).status()).isEqualTo(201);
        Resp second = post(BASE + "/candidates/" + two + "/offers", director, offerBody("15000"));

        assertThat(second.status()).isEqualTo(409);
        Integer open = jdbc.queryForObject(
                "select count(*) from job_offer where status in ('DRAFT','ISSUED') and phone_key = ?",
                Integer.class,
                phone.substring(phone.length() - 10));
        assertThat(open).isEqualTo(1);
    }

    @Test
    void aDraftCanBeChangedButAnIssuedOfferCannotEvenBySql() {
        String admin = admin();
        String director = directorToken();
        UUID candidate = selected(admin, phone());
        Resp draft = post(BASE + "/candidates/" + candidate + "/offers", director, offerBody("15000"));

        Resp changed = put(BASE + "/offers/" + draft.id(), director, offerBody("17000"));
        assertThat(changed.status()).as(changed.body()).isEqualTo(200);
        assertThat(changed.map()).containsEntry("monthlySalary", "17000.00");

        assertThat(post(BASE + "/offers/" + draft.id() + "/issue", director, Map.of()).status()).isEqualTo(200);
        assertThat(put(BASE + "/offers/" + draft.id(), director, offerBody("19000")).status()).isEqualTo(409);
        assertThatThrownBy(() -> jdbc.update("update job_offer set monthly_salary = 99999 where id = ?", draft.id()))
                .hasMessageContaining("cannot be changed");
        assertThat(jdbc.queryForObject("select monthly_salary from job_offer where id = ?", java.math.BigDecimal.class, draft.id()))
                .isEqualByComparingTo("17000");
    }

    @Test
    void supersedeCreatesANewIssuedOfferAndKeepsTheOldOneVisible() {
        String admin = admin();
        String director = directorToken();
        UUID candidate = selected(admin, phone());
        UUID first = issued(director, candidate, "15000");

        Resp second = post(BASE + "/offers/" + first + "/supersede", director, offerBody("18000"));

        assertThat(second.status()).as(second.body()).isEqualTo(201);
        assertThat(second.map()).containsEntry("status", "ISSUED").containsEntry("supersedesId", first.toString());
        Resp all = get(BASE + "/offers?candidate=" + candidate, admin);
        assertThat(all.body()).contains("SUPERSEDED", "15000.00", "18000.00");
        assertThat(post(BASE + "/offers/" + first + "/supersede", director, offerBody("19000")).status()).isEqualTo(409);
    }

    @Test
    void declineNeedsAReasonAndClosesTheOffer() {
        String admin = admin();
        String director = directorToken();
        UUID offer = issued(director, selected(admin, phone()), "15000");

        assertThat(post(BASE + "/offers/" + offer + "/decline", director, Map.of("reason", " ")).status()).isEqualTo(400);
        Resp declined = post(BASE + "/offers/" + offer + "/decline", director, Map.of("reason", "Took another job"));

        assertThat(declined.status()).as(declined.body()).isEqualTo(200);
        assertThat(declined.map()).containsEntry("status", "DECLINED").containsEntry("declineReason", "Took another job");
        assertThat(post(BASE + "/offers/" + offer + "/accept", director, Map.of()).status()).isEqualTo(409);
    }

    @Test
    void acceptingCreatesOneTeacherInTrainingWithNoSalaryEntryAndTwiceCreatesNoSecond() {
        String admin = admin();
        String director = directorToken();
        UUID candidate = selected(admin, phone());
        UUID offer = issued(director, candidate, "15000");

        Resp accepted = post(BASE + "/offers/" + offer + "/accept", director, Map.of());

        assertThat(accepted.status()).as(accepted.body()).isEqualTo(200);
        assertThat(accepted.map()).containsEntry("status", "ACCEPTED").containsKey("teacherId");
        UUID teacher = UUID.fromString((String) accepted.map().get("teacherId"));
        assertThat(jdbc.queryForObject("select status from teacher where id = ?", String.class, teacher)).isEqualTo("IN_TRAINING");
        assertThat(registry.hasSalaryEntry(teacher)).isFalse();
        Resp again = post(BASE + "/offers/" + offer + "/accept", director, Map.of());
        assertThat(again.status()).isEqualTo(200);
        assertThat(again.map()).containsEntry("teacherId", teacher.toString());
        Integer teachers = jdbc.queryForObject(
                "select count(*) from teacher where phone = (select phone from candidate where id = ?)", Integer.class, candidate);
        assertThat(teachers).isEqualTo(1);
        assertThat(get(BASE + "/candidates?drive=" + jdbc.queryForObject("select drive_id from candidate where id = ?", UUID.class, candidate), admin).body())
                .contains(teacher.toString());
    }

    @Test
    void anExistingWorkingTeacherBlocksAcceptanceAndNothingIsCreated() {
        String admin = admin();
        String director = directorToken();
        String phone = phone();
        UUID existing = registry.createTrainee(signInAs(Role.ADMIN).userId(), new TeacherRegistry.Candidate("Already Here", phone, null, null));
        registry.activate(signInAs(Role.ADMIN).userId(), existing);
        UUID offer = issued(director, selected(admin, phone), "15000");

        Resp refused = post(BASE + "/offers/" + offer + "/accept", director, Map.of());

        assertThat(refused.status()).isEqualTo(409);
        assertThat(refused.body()).contains("Already Here");
        assertThat(jdbc.queryForObject("select status from job_offer where id = ?", String.class, offer)).isEqualTo("ISSUED");
        assertThat(jdbc.queryForObject("select count(*) from teacher where phone = ?", Integer.class, phone)).isEqualTo(1);
    }

    @Test
    void anExitedTeacherNeedsConfirmationAndTheOldRecordIsUnchanged() {
        String admin = admin();
        String director = directorToken();
        String phone = phone();
        UUID actor = signInAs(Role.ADMIN).userId();
        UUID old = registry.createTrainee(actor, new TeacherRegistry.Candidate("Came Back", phone, null, null));
        registry.exit(actor, old, LocalDate.now(), "Left");
        UUID offer = issued(director, selected(admin, phone), "15000");

        Resp unconfirmed = post(BASE + "/offers/" + offer + "/accept", director, Map.of());
        assertThat(unconfirmed.status()).isEqualTo(409);
        assertThat(unconfirmed.body()).contains("Confirm");

        Resp confirmed = post(BASE + "/offers/" + offer + "/accept", director, Map.of("confirmNewRecord", true));
        assertThat(confirmed.status()).as(confirmed.body()).isEqualTo(200);
        assertThat(confirmed.map().get("teacherId")).isNotEqualTo(old.toString());
        assertThat(jdbc.queryForObject("select status from teacher where id = ?", String.class, old)).isEqualTo("EXITED");
    }

    @Test
    void theRoleMatrixHasTheDirectorWritingAdminAndManagerOnlyReadingAndTeacherSystemRefused() {
        String admin = admin();
        String director = directorToken();
        String manager = signInAs(Role.MANAGER).token();
        UUID candidate = selected(admin, phone());
        UUID offer = issued(director, candidate, "15000");

        for (String reader : List.of(admin, manager, director)) {
            assertThat(get(BASE + "/offers", reader).status()).isEqualTo(200);
            assertThat(get(BASE + "/offers/" + offer + "/letter", reader).status()).isEqualTo(200);
        }
        for (String blocked : List.of(admin, manager)) {
            assertThat(post(BASE + "/candidates/" + candidate + "/offers", blocked, offerBody("1")).status()).isEqualTo(403);
            assertThat(post(BASE + "/offers/" + offer + "/issue", blocked, Map.of()).status()).isEqualTo(403);
            assertThat(post(BASE + "/offers/" + offer + "/supersede", blocked, offerBody("1")).status()).isEqualTo(403);
            assertThat(post(BASE + "/offers/" + offer + "/accept", blocked, Map.of()).status()).isEqualTo(403);
            assertThat(post(BASE + "/offers/" + offer + "/decline", blocked, Map.of("reason", "x")).status()).isEqualTo(403);
        }
        for (Role role : List.of(Role.TEACHER, Role.SYSTEM)) {
            String token = signInAs(role).token();
            assertThat(get(BASE + "/offers", token).status()).as(role.name()).isEqualTo(403);
            assertThat(get(BASE + "/offers/" + offer + "/letter", token).status()).as(role.name()).isEqualTo(403);
        }
        assertThat(get(BASE + "/offers", null).status()).isEqualTo(401);
    }

    @Test
    void theLetterShowsThePackageEscapesUserTextAndFollowsADraftChange() {
        String admin = admin();
        String director = directorToken();
        UUID candidate = selected(admin, phone());
        Map<String, Object> body = offerBody("1234567");
        body.put("terms", "<script>alert(1)</script> & more");
        Resp draft = post(BASE + "/candidates/" + candidate + "/offers", director, body);

        Resp letter = get(BASE + "/offers/" + draft.id() + "/letter", director);

        assertThat(letter.status()).isEqualTo(200);
        assertThat(letter.body()).contains("Trainee / English Trainer", "Rs. 12,34,567.00", "Travel 1000", "Status: DRAFT")
                .contains("&lt;script&gt;alert(1)&lt;/script&gt; &amp; more")
                .doesNotContain("<script>");
        assertThat(letter.body()).contains("one-month induction");

        put(BASE + "/offers/" + draft.id(), director, offerBody("20000"));
        assertThat(get(BASE + "/offers/" + draft.id() + "/letter", director).body()).contains("Rs. 20,000.00");
    }

    @Test
    void noStipendFieldExists() {
        Integer columns = jdbc.queryForObject(
                "select count(*) from information_schema.columns where table_name = 'job_offer' and column_name like '%stipend%'",
                Integer.class);
        assertThat(columns).isZero();
    }
}
