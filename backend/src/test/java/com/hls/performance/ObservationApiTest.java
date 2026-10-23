package com.hls.performance;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import com.hls.support.PerformanceTestBase;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Spec 026 US1: the observation form, from Draft to Submitted, the visit not held, corrections, files and scope. */
class ObservationApiTest extends PerformanceTestBase {

    @Test
    void aManagerStartsFillsAndSubmitsAnObservationWithoutPermission() {
        Setup s = setup();
        Resp started = startObservation(s.managerToken(), s.teacherId());
        assertThat(started.status()).as(started.body()).isEqualTo(201);
        Map<String, Object> draft = started.map();
        assertThat(draft.get("status")).isEqualTo("DRAFT");
        assertThat(draft.get("teacherName")).isNotNull();
        assertThat(draft.get("schoolName")).isNotNull();
        assertThat(draft.get("observerUserId")).isEqualTo(s.managerUserId().toString());
        assertThat(draft.get("permissionLabel")).isEqualTo("Permission not recorded");

        Resp submitted = submitObservation(s.managerToken(), id(draft), form("GOOD", 4));
        assertThat(submitted.status()).as(submitted.body()).isEqualTo(200);
        Map<String, Object> view = submitted.map();
        assertThat(view.get("status")).isEqualTo("SUBMITTED");
        assertThat(view.get("overallResult")).isEqualTo("GOOD");
        assertThat(new BigDecimal(view.get("averageRating").toString())).isEqualByComparingTo("4.0");
        assertThat(view.get("permissionRecorded")).isEqualTo(false);
        assertThat(view.get("permissionLabel")).isEqualTo("Permission not recorded");
        assertThat(view.get("issueId")).isNull();
        assertThat(((Map<?, ?>) view.get("actions")).get("edit")).isEqualTo(false);
        assertThat(jdbc.queryForObject("select count(*) from performance_issue where teacher_id = ?", Integer.class, s.teacherId())).isZero();
        assertChangeRecorded(s.admin(), "OBSERVATION", id(view), "status");
    }

    @Test
    void thePermissionIsOptionalButShownWhenGiven() {
        Setup s = setup();
        UUID draft = newDraft(s.managerToken(), s.teacherId());
        Map<String, Object> body = form("GOOD", 5);
        body.put("permission", Map.of("by", "Mrs Rao", "way", "PHONE", "on", today().toString()));
        Resp resp = submitObservation(s.managerToken(), draft, body);
        assertThat(resp.status()).as(resp.body()).isEqualTo(200);
        assertThat(resp.map().get("permissionRecorded")).isEqualTo(true);
        assertThat(resp.map().get("permissionBy")).isEqualTo("Mrs Rao");
        assertThat(resp.map().get("permissionWay")).isEqualTo("PHONE");
        assertThat(resp.map().get("permissionLabel")).isNull();

        // a way without a name is refused, so a half-recorded permission is never stored
        UUID second = newDraft(s.managerToken(), s.teacherId());
        Map<String, Object> half = form("GOOD", 5);
        half.put("permission", Map.of("way", "PHONE"));
        assertThat(submitObservation(s.managerToken(), second, half).status()).isEqualTo(400);
    }

    @Test
    void theAverageAndSuggestedResultAreAGuideOnly() {
        Setup s = setup();
        record Case(Map<String, Integer> ratings, String average, String suggested) {}
        List<Case> cases = List.of(
                new Case(ratings(4), "4.0", "GOOD"),
                new Case(Map.of("TEACHING_ABILITY", 2, "PUNCTUALITY", 3), "2.5", "NEEDS_IMPROVEMENT"),
                new Case(Map.of("TEACHING_ABILITY", 1, "PUNCTUALITY", 2), "1.5", "UNSUITABLE"),
                new Case(Map.of("TEACHING_ABILITY", 5), "5.0", "GOOD"));
        for (Case c : cases) {
            Resp resp = post(PERF + "/observations/preview", s.managerToken(), Map.of("ratings", c.ratings()));
            assertThat(resp.status()).as(resp.body()).isEqualTo(200);
            assertThat(new BigDecimal(resp.map().get("average").toString())).isEqualByComparingTo(c.average());
            assertThat(resp.map().get("suggestedResult")).isEqualTo(c.suggested());
        }
        assertThat(post(PERF + "/observations/preview", s.managerToken(), Map.of("ratings", Map.of())).map().get("average")).isNull();

        // the observer's choice is stored, whatever the guide says
        UUID draft = newDraft(s.managerToken(), s.teacherId());
        Resp resp = submitObservation(s.managerToken(), draft, form("GOOD", 1));
        assertThat(resp.status()).as(resp.body()).isEqualTo(200);
        assertThat(resp.map().get("overallResult")).isEqualTo("GOOD");
        assertThat(resp.map().get("suggestedResult")).isEqualTo("UNSUITABLE");
    }

    @Test
    void anAreaNotObservedIsLeftOutOfTheAverage() {
        Setup s = setup();
        UUID draft = newDraft(s.managerToken(), s.teacherId());
        Map<String, Object> body = form("GOOD", 4);
        Map<String, Integer> partial = new HashMap<>();
        partial.put("TEACHING_ABILITY", 5);
        partial.put("PUNCTUALITY", 3);
        partial.put("CONFIDENCE", null);
        body.put("ratings", partial);
        Resp resp = submitObservation(s.managerToken(), draft, body);
        assertThat(resp.status()).as(resp.body()).isEqualTo(200);
        assertThat(new BigDecimal(resp.map().get("averageRating").toString())).isEqualByComparingTo("4.0");
        assertThat(((Map<?, ?>) resp.map().get("ratings")).get("CONFIDENCE")).isNull();
    }

    @Test
    void aFormThatIsNotCompleteCannotBeSubmitted() {
        Setup s = setup();
        UUID draft = newDraft(s.managerToken(), s.teacherId());

        Map<String, Object> noRatings = form("GOOD", 4);
        noRatings.put("ratings", Map.of());
        assertThat(submitObservation(s.managerToken(), draft, noRatings).status()).isEqualTo(400);

        Map<String, Object> noResult = form("GOOD", 4);
        noResult.put("overallResult", null);
        assertThat(submitObservation(s.managerToken(), draft, noResult).status()).isEqualTo(400);

        Map<String, Object> noText = form("GOOD", 4);
        noText.put("strengths", " ");
        assertThat(submitObservation(s.managerToken(), draft, noText).status()).isEqualTo(400);

        Map<String, Object> badRating = form("GOOD", 4);
        badRating.put("ratings", Map.of("TEACHING_ABILITY", 6));
        assertThat(submitObservation(s.managerToken(), draft, badRating).status()).isEqualTo(400);

        Map<String, Object> badArea = form("GOOD", 4);
        badArea.put("ratings", Map.of("MAGIC", 3));
        assertThat(submitObservation(s.managerToken(), draft, badArea).status()).isEqualTo(400);

        Map<String, Object> shortClass = form("GOOD", 4);
        shortClass.put("durationMinutes", 4);
        assertThat(submitObservation(s.managerToken(), draft, shortClass).status()).isEqualTo(400);
        shortClass.put("durationMinutes", 241);
        assertThat(submitObservation(s.managerToken(), draft, shortClass).status()).isEqualTo(400);

        Map<String, Object> tooLong = form("GOOD", 4);
        tooLong.put("comments", "x".repeat(2001));
        assertThat(submitObservation(s.managerToken(), draft, tooLong).status()).isEqualTo(400);

        // still a draft, and it can be completed afterwards
        assertThat(get(PERF + "/observations/" + draft, s.managerToken()).map().get("status")).isEqualTo("DRAFT");
        assertThat(submitObservation(s.managerToken(), draft, form("GOOD", 4)).status()).isEqualTo(200);
    }

    @Test
    void aDraftIsSavedAndChangedOnlyByItsObserver() {
        Setup s = setup();
        UUID draft = newDraft(s.managerToken(), s.teacherId());
        Object version = get(PERF + "/observations/" + draft, s.managerToken()).map().get("version");
        Resp saved = put(PERF + "/observations/" + draft, s.managerToken(), withVersion(form("GOOD", 3), version));
        assertThat(saved.status()).as(saved.body()).isEqualTo(200);
        assertThat(saved.map().get("className")).isEqualTo("Class 5");
        assertThat(saved.map().get("status")).isEqualTo("DRAFT");

        // a stale version is refused, and so is anyone else, even Admin
        assertThat(put(PERF + "/observations/" + draft, s.managerToken(), withVersion(form("GOOD", 3), version)).status()).isEqualTo(409);
        Object fresh = saved.map().get("version");
        assertThat(put(PERF + "/observations/" + draft, s.admin(), withVersion(form("GOOD", 3), fresh)).status()).isEqualTo(403);
    }

    @Test
    void aVisitNotHeldNeedsNoRatingsAndNeverBlocksAnotherObservation() {
        Setup s = setup();
        assertThat(plan(s.director(), s.teacherId(), today().plusDays(2), "MANAGEMENT_PLAN").status()).isEqualTo(201);
        UUID draft = newDraft(s.managerToken(), s.teacherId());
        Object version = get(PERF + "/observations/" + draft, s.managerToken()).map().get("version");

        assertThat(post(PERF + "/observations/" + draft + "/not-held", s.managerToken(), Map.of("version", version)).status()).isEqualTo(400);
        Resp resp = post(PERF + "/observations/" + draft + "/not-held", s.managerToken(),
                Map.of("reason", "PRINCIPAL_DECLINED", "text", "Principal asked us to come next week", "version", version));
        assertThat(resp.status()).as(resp.body()).isEqualTo(200);
        assertThat(resp.map().get("status")).isEqualTo("NOT_HELD");
        assertThat(resp.map().get("principalDeclined")).isEqualTo(true);
        assertThat(resp.map().get("overallResult")).isNull();

        // the plan and its task stay open, and the attempt shows in the declined filter
        assertThat(openObservationTasks(s.teacherId())).isEqualTo(1);
        Resp declined = get(PERF + "/observations?principalDeclined=true&teacherId=" + s.teacherId(), s.director());
        assertThat(content(declined)).hasSize(1);

        // declining never blocks a further observation
        assertThat(startObservation(s.managerToken(), s.teacherId()).status()).isEqualTo(201);

        // the reason OTHER needs words
        UUID other = newDraft(s.managerToken(), s.teacherId());
        Object v2 = get(PERF + "/observations/" + other, s.managerToken()).map().get("version");
        assertThat(post(PERF + "/observations/" + other + "/not-held", s.managerToken(), Map.of("reason", "OTHER", "version", v2)).status())
                .isEqualTo(400);
    }

    @Test
    void aCorrectionIsANewVersionAndTheEarlierOneStaysForAdminAndDirector() {
        Setup s = setup();
        Map<String, Object> first = observe(s.managerToken(), s.teacherId(), "GOOD", 4);
        UUID id = id(first);

        Map<String, Object> corrected = form("GOOD", 5);
        Object version = first.get("version");
        Map<String, Object> noReason = new HashMap<>();
        noReason.put("form", withVersion(corrected, version));
        assertThat(post(PERF + "/observations/" + id + "/correct", s.managerToken(), noReason).status()).isEqualTo(400);

        Map<String, Object> body = new HashMap<>(noReason);
        body.put("reason", "Entered the wrong ratings");
        Resp resp = post(PERF + "/observations/" + id + "/correct", s.managerToken(), body);
        assertThat(resp.status()).as(resp.body()).isEqualTo(200);
        assertThat(resp.map().get("versionNo")).isEqualTo(2);
        assertThat(resp.map().get("current")).isEqualTo(true);
        assertThat(new BigDecimal(resp.map().get("averageRating").toString())).isEqualByComparingTo("5.0");
        assertThat(resp.map().get("correctionReason")).isEqualTo("Entered the wrong ratings");
        assertThat(resp.map().get("id")).isNotEqualTo(first.get("id"));

        // the old row is kept as SUPERSEDED and is no longer in the lists
        assertThat(jdbc.queryForObject("select status from observation where id = ?", String.class, id)).isEqualTo("SUPERSEDED");
        assertThat(content(get(PERF + "/observations?teacherId=" + s.teacherId(), s.director()))).hasSize(1);

        // Admin and Director see both versions, a Manager does not
        Resp versions = get(PERF + "/observations/" + resp.map().get("id") + "?versions=true", s.director());
        assertThat(versions.status()).isEqualTo(200);
        assertThat(versions.body()).contains("\"versionNo\":1").contains("\"versionNo\":2");
        assertThat(get(PERF + "/observations/" + resp.map().get("id") + "?versions=true", s.managerToken()).status()).isEqualTo(403);

        // an earlier version cannot be corrected again
        assertThat(post(PERF + "/observations/" + id + "/correct", s.managerToken(), body).status()).isEqualTo(409);
    }

    @Test
    void filesAttachToADraftUpToTheLimit() {
        Setup s = setup();
        UUID draft = newDraft(s.managerToken(), s.teacherId());
        for (int i = 0; i < 5; i++) {
            assertThat(upload(s.managerToken(), draft, "notes" + i + ".txt").status()).isEqualTo(201);
        }
        assertThat(upload(s.managerToken(), draft, "notes6.txt").status()).isEqualTo(409);
        Map<String, Object> view = get(PERF + "/observations/" + draft, s.managerToken()).map();
        List<?> files = (List<?>) view.get("attachments");
        assertThat(files).hasSize(5);
        String fileId = (String) ((Map<?, ?>) files.get(0)).get("fileId");
        assertThat(get(PERF + "/observations/" + draft + "/attachments/" + fileId, s.director()).status()).isEqualTo(200);
        assertThat(delete(PERF + "/observations/" + draft + "/attachments/" + fileId + "?reason=wrong+file", s.managerToken()).status()).isEqualTo(204);
        assertThat(((List<?>) get(PERF + "/observations/" + draft, s.managerToken()).map().get("attachments"))).hasSize(4);
    }

    @Test
    void scopeAndRolesAreEnforcedOnEveryObservationRoute() {
        Setup s = setup();
        Setup other = setup();
        Map<String, Object> submitted = observe(s.managerToken(), s.teacherId(), "GOOD", 4);
        UUID id = id(submitted);

        // another Manager cannot start, read or list what is not theirs
        assertThat(startObservation(other.managerToken(), s.teacherId()).status()).isEqualTo(404);
        assertThat(get(PERF + "/observations/" + id, other.managerToken()).status()).isEqualTo(404);
        assertThat(content(get(PERF + "/observations", other.managerToken())).stream().map(r -> r.get("id")).toList()).doesNotContain(id.toString());

        // Teacher and System are refused before any lookup
        String teacher = signInAs(Role.TEACHER).token();
        String system = signInAs(Role.SYSTEM).token();
        for (String token : List.of(teacher, system)) {
            assertThat(get(PERF + "/observations", token).status()).isEqualTo(403);
            assertThat(get(PERF + "/observations/" + id, token).status()).isEqualTo(403);
            assertThat(startObservation(token, s.teacherId()).status()).isEqualTo(403);
        }

        // Admin and Director see it
        assertThat(get(PERF + "/observations/" + id, s.admin()).status()).isEqualTo(200);
        assertThat(get(PERF + "/observations/" + id, s.director()).status()).isEqualTo(200);
    }

    @Test
    void onlyAnActivePlacedTeacherCanBeObserved() {
        Setup s = setup();
        UUID unplaced = teacher(s.admin());
        assertThat(startObservation(s.admin(), unplaced).status()).isEqualTo(400);
        assertThat(startObservation(s.admin(), UUID.randomUUID()).status()).isEqualTo(404);
    }
}
