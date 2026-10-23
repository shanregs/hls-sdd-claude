package com.hls.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Helpers for the spec 015 tests: claims through the API (create, add a bill, submit, approve), users for each role and
 * an area, and the payroll fixtures of spec 013b for the reimbursement tests. The file store directory is set once for every integration test in {@link IntegrationTestBase}.
 */
public abstract class ExpensesTestBase extends PayrollRunTestBase {

    protected static final String MINE = "/api/v1/my-expenses";
    protected static final String REVIEW = "/api/v1/expenses";

    /** The first bytes of a JPEG, enough for the file store content check. */
    protected static final byte[] JPG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16, 'J', 'F', 'I', 'F', 0};

    protected Map<String, Object> claimBody(LocalDate date, String amount, String category) {
        Map<String, Object> body = new HashMap<>();
        body.put("clientRequestId", UUID.randomUUID().toString());
        body.put("expenseDate", date.toString());
        body.put("amount", amount);
        body.put("category", category);
        body.put("paidBy", "CLAIMANT");
        body.put("note", "Expense " + uniqueName("claim"));
        return body;
    }

    protected Map<String, Object> travelBody(String amount) {
        return claimBody(today().minusDays(1), amount, "TRAVEL_FUEL");
    }

    protected Resp createClaim(String token, Map<String, Object> body) {
        return post(MINE, token, body);
    }

    /** A Draft claim; returns its id. */
    protected UUID newDraft(String token, Map<String, Object> body) {
        Resp resp = createClaim(token, body);
        assertThat(resp.status()).as(resp.body()).isEqualTo(201);
        return resp.id();
    }

    /** A Draft with one jpg bill, ready to submit. */
    protected UUID newReadyClaim(String token, String amount) {
        UUID id = newDraft(token, travelBody(amount));
        assertThat(uploadBill(token, id, "bill.jpg", JPG).status()).isEqualTo(201);
        return id;
    }

    protected Resp submit(String token, UUID id) {
        return post(MINE + "/" + id + "/submit", token, Map.of("version", versionOf(token, id)));
    }

    /** A claim with a bill that has been submitted. */
    protected UUID newSubmittedClaim(String token, String amount) {
        UUID id = newReadyClaim(token, amount);
        Resp resp = submit(token, id);
        assertThat(resp.status()).as(resp.body()).isEqualTo(200);
        return id;
    }

    protected long versionOf(String token, UUID id) {
        Resp resp = get(MINE + "/" + id, token);
        assertThat(resp.status()).as(resp.body()).isEqualTo(200);
        return ((Number) resp.map().get("version")).longValue();
    }

    protected long reviewVersion(String token, UUID id) {
        Resp resp = get(REVIEW + "/" + id, token);
        assertThat(resp.status()).as(resp.body()).isEqualTo(200);
        return ((Number) resp.map().get("version")).longValue();
    }

    protected Resp approve(String token, UUID id) {
        return post(REVIEW + "/" + id + "/approve", token, Map.of("version", reviewVersion(token, id)));
    }

    protected Resp reject(String token, UUID id, String reason) {
        Map<String, Object> body = new HashMap<>();
        body.put("version", reviewVersion(token, id));
        body.put("reason", reason);
        return post(REVIEW + "/" + id + "/reject", token, body);
    }

    protected Resp reverse(String token, UUID id, String reason) {
        Map<String, Object> body = new HashMap<>();
        body.put("version", reviewVersion(token, id));
        body.put("reason", reason);
        return post(REVIEW + "/" + id + "/reverse", token, body);
    }

    protected Resp recordPaid(String token, UUID id, LocalDate on, String mode, String reference) {
        Map<String, Object> body = new HashMap<>();
        body.put("version", reviewVersion(token, id));
        body.put("paidOn", on.toString());
        body.put("mode", mode);
        body.put("reference", reference);
        return post(REVIEW + "/" + id + "/record-paid", token, body);
    }

    protected String statusOf(String token, UUID id) {
        Resp resp = get(MINE + "/" + id, token);
        assertThat(resp.status()).as(resp.body()).isEqualTo(200);
        return (String) resp.map().get("status");
    }

    protected String reviewStatusOf(String token, UUID id) {
        Resp resp = get(REVIEW + "/" + id, token);
        assertThat(resp.status()).as(resp.body()).isEqualTo(200);
        return (String) resp.map().get("status");
    }

    @SuppressWarnings("unchecked")
    protected static List<String> allowedActions(Resp detail) {
        return (List<String>) detail.map().get("allowedActions");
    }

    @SuppressWarnings("unchecked")
    protected static List<Map<String, Object>> historyOf(Resp detail) {
        return (List<Map<String, Object>>) detail.map().get("history");
    }

    @SuppressWarnings("unchecked")
    protected static List<Map<String, Object>> billsOf(Resp detail) {
        return (List<Map<String, Object>>) detail.map().get("bills");
    }

    protected Resp uploadBill(String token, UUID claim, String name, byte[] content) {
        return uploadTo(MINE + "/" + claim + "/bills", token, name, content);
    }

    protected Resp uploadTo(String uri, String token, String name, byte[] content) {
        String boundary = "----hls" + UUID.randomUUID();
        String crlf = String.valueOf((char) 13) + (char) 10;
        byte[] head = ("--" + boundary + crlf + "Content-Disposition: form-data; name=\"file\"; filename=\"" + name + "\"" + crlf
                        + "Content-Type: application/octet-stream" + crlf + crlf)
                .getBytes(StandardCharsets.UTF_8);
        byte[] tail = (crlf + "--" + boundary + "--" + crlf).getBytes(StandardCharsets.UTF_8);
        byte[] body = new byte[head.length + content.length + tail.length];
        System.arraycopy(head, 0, body, 0, head.length);
        System.arraycopy(content, 0, body, head.length, content.length);
        System.arraycopy(tail, 0, body, head.length + content.length, tail.length);
        var result = client.post()
                .uri(uri)
                .header("Authorization", "Bearer " + token)
                .contentType(org.springframework.http.MediaType.parseMediaType("multipart/form-data; boundary=" + boundary))
                .body(body)
                .exchange()
                .returnResult(String.class);
        return new Resp(result.getStatus().value(), result.getResponseBody());
    }

    protected Signed admin() {
        return signInAs(Role.ADMIN);
    }

    protected Signed director() {
        return signInAs(Role.DIRECTOR);
    }

    protected Signed teacherUser() {
        return signInAs(Role.TEACHER);
    }

    protected int notificationCount(UUID user, String type) {
        return jdbc.queryForObject(
                "select count(*) from notification where recipient_user_id = ? and type = ?", Integer.class, user, type);
    }

    protected int eventCount(UUID claim, String event) {
        return jdbc.queryForObject(
                "select count(*) from expense_claim_event where claim_id = ? and event = ?", Integer.class, claim, event);
    }

    // ------------------------------------------------------------------ links: batches and marketing visits

    /** An induction batch written directly (the induction endpoints need recruits we do not need here). */
    protected UUID insertBatch(String name, LocalDate startsOn, LocalDate endsOn) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "insert into induction_batch (id, name, starts_on, ends_on, venue_type, seat_limit, status, created_at)"
                        + " values (?, ?, ?, ?, 'PHYSICAL', 10, 'PLANNED', now())",
                id, name, startsOn, endsOn);
        return id;
    }

    /** A Teacher signed off as having completed the batch. */
    protected void insertCompleter(UUID batch, LocalDate startsOn, LocalDate endsOn) {
        jdbc.update(
                "insert into induction_enrolment (id, batch_id, teacher_id, starts_on, ends_on, enrolled_by, enrolled_at, result)"
                        + " values (?, ?, ?, ?, ?, ?, now(), 'COMPLETED')",
                UUID.randomUUID(), batch, UUID.randomUUID(), startsOn, endsOn, UUID.randomUUID());
    }

    /** A marketing visit planned by the token's user on the date; returns {activityId, prospectName}. */
    protected Object[] marketingVisit(String token, LocalDate date) {
        String name = uniqueName("Prospect School");
        Map<String, Object> prospect = new HashMap<>();
        prospect.put("name", name);
        prospect.put("board", "CBSE");
        prospect.put("address", "5 Lake Road");
        prospect.put("zoneId", zone(token));
        prospect.put("contactPerson", "Mrs Rao");
        prospect.put("designation", "Principal");
        prospect.put("phone", "9111111111");
        prospect.put("email", "rao@school.test");
        prospect.put("expectedTeachers", 4);
        Resp created = post("/api/v1/marketing/prospects", token, prospect);
        assertThat(created.status()).as(created.body()).isEqualTo(201);
        String prospectId = (String) ((Map<?, ?>) created.map().get("row")).get("id");
        Map<String, Object> activity = new HashMap<>();
        activity.put("prospectId", prospectId);
        activity.put("type", "VISIT");
        activity.put("date", date.toString());
        activity.put("notes", "Meet the principal");
        Resp visit = post("/api/v1/marketing/activities", token, activity);
        assertThat(visit.status()).as(visit.body()).isEqualTo(201);
        return new Object[] {visit.id(), name};
    }

    protected Map<String, Object> linkTo(String kind, UUID ref) {
        Map<String, Object> link = new HashMap<>();
        link.put("kind", kind);
        link.put("ref", ref.toString());
        return link;
    }
}
