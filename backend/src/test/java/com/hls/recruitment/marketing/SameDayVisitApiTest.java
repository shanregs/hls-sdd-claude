package com.hls.recruitment.marketing;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

/** Spec 040 US3: a visit the Manager logs for today with no plan is saved already Completed. */
class SameDayVisitApiTest extends MarketingTestBase {

    private Map<String, Object> body(UUID prospect, String outcome) {
        Map<String, Object> body = new HashMap<>();
        body.put("prospectId", prospect.toString());
        body.put("type", "VISIT");
        body.put("outcome", outcome);
        body.put("notes", "Walked the school");
        return body;
    }

    private Resp postKeyed(String token, UUID key, Object payload) {
        var spec = client.method(HttpMethod.POST).uri(M + "/activities/same-day").header("Authorization", "Bearer " + token);
        spec.header("Idempotency-Key", key.toString());
        var result = spec.body(payload).exchange().returnResult(String.class);
        return new Resp(result.getStatus().value(), result.getResponseBody());
    }

    @Test
    void aManagerLogsACompletedVisitForTodayInTheirZone() {
        String admin = admin();
        UUID zone = zone(admin);
        UUID prospect = prospect(admin, zone);
        ManagerCtx manager = newManager(admin, zone);

        Resp resp = post(M + "/activities/same-day", manager.token(), body(prospect, "Met the Principal; wants a proposal"));

        assertThat(resp.status()).as(resp.body()).isEqualTo(201);
        assertThat(resp.map()).containsEntry("status", "COMPLETED").containsEntry("sameDay", true);
        assertThat(resp.map().get("date")).isEqualTo(today.toString());
        Resp listed = get(M + "/activities?sameDay=true&from=" + today + "&to=" + today, manager.token());
        assertThat(listed.body()).contains((String) resp.map().get("id"));
        Resp calendar = get("/api/v1/activity-calendar?sameDay=true", admin);
        assertThat(calendar.status()).as(calendar.body()).isEqualTo(200);
        assertThat(calendar.body()).contains((String) resp.map().get("id")).contains("\"sameDay\":true");
    }

    @Test
    void anOutcomeIsRequired() {
        String admin = admin();
        UUID zone = zone(admin);
        UUID prospect = prospect(admin, zone);
        ManagerCtx manager = newManager(admin, zone);

        assertThat(post(M + "/activities/same-day", manager.token(), body(prospect, " ")).status()).isEqualTo(400);
    }

    @Test
    void aProspectOutsideTheManagersZonesIsNotFound() {
        String admin = admin();
        UUID prospect = prospect(admin, zone(admin));
        ManagerCtx manager = newManager(admin, zone(admin));

        assertThat(post(M + "/activities/same-day", manager.token(), body(prospect, "Met the Principal")).status())
                .isEqualTo(404);
    }

    @Test
    void adminCannotLogAVisitOnBehalfOfAManager() {
        String admin = admin();
        UUID prospect = prospect(admin, zone(admin));

        assertThat(post(M + "/activities/same-day", admin, body(prospect, "Met the Principal")).status()).isEqualTo(403);
    }

    @Test
    void aRepeatedPhoneKeyCreatesOneVisitAndAStaleDateIsRefused() {
        String admin = admin();
        UUID zone = zone(admin);
        UUID prospect = prospect(admin, zone);
        ManagerCtx manager = newManager(admin, zone);
        UUID key = UUID.randomUUID();
        Map<String, Object> payload = body(prospect, "Met the Principal; wants a proposal");
        payload.put("capturedOn", today.minusDays(2).toString());

        Resp first = postKeyed(manager.token(), key, payload);
        Resp again = postKeyed(manager.token(), key, payload);

        assertThat(first.status()).as(first.body()).isEqualTo(201);
        assertThat(again.status()).isEqualTo(201);
        assertThat(again.map().get("id")).isEqualTo(first.map().get("id"));
        assertThat(jdbc.queryForObject("select count(*) from marketing_activity where client_key = ?", Integer.class, key))
                .isEqualTo(1);
        assertThat(first.map().get("date")).isEqualTo(today.minusDays(2).toString());

        Map<String, Object> stale = body(prospect, "Met the Principal");
        stale.put("capturedOn", today.minusDays(8).toString());
        assertThat(postKeyed(manager.token(), UUID.randomUUID(), stale).status()).isEqualTo(409);
    }
}
