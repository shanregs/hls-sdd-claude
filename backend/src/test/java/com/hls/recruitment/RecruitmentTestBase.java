package com.hls.recruitment;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.http.MediaType;

/** Helpers for the spec 016 integration tests: colleges, drives, candidates, CSV upload. */
public abstract class RecruitmentTestBase extends com.hls.schoolbilling.SchoolContractsTestBase {

    protected static final String BASE = "/api/v1/recruitment";
    private static final AtomicLong SEQ = new AtomicLong(8_100_000_000L);

    protected static String phone() {
        return Long.toString(SEQ.incrementAndGet());
    }

    protected UUID college(String token) {
        Map<String, Object> body = new HashMap<>();
        body.put("name", uniqueName("College"));
        body.put("city", "Chennai");
        body.put("placementOfficer", Map.of("name", "P. Officer", "phone", "9000000001", "email", "po@college.test"));
        body.put("principal", Map.of("name", "Dr. Principal", "phone", "9000000002"));
        Resp resp = post(BASE + "/colleges", token, body);
        assertThat(resp.status()).as(resp.body()).isEqualTo(201);
        return resp.id();
    }

    protected Map<String, Object> driveBody(UUID college, List<UUID> interviewers, LocalDate... dates) {
        Map<String, Object> body = new HashMap<>();
        body.put("collegeId", college);
        body.put("dates", List.of(dates).stream().map(LocalDate::toString).toList());
        body.put("venue", "Seminar Hall");
        body.put("season", "2026-27");
        body.put("interviewerUserIds", interviewers);
        return body;
    }

    protected UUID drive(String token, UUID college, List<UUID> interviewers, LocalDate... dates) {
        Resp resp = post(BASE + "/drives", token, driveBody(college, interviewers, dates));
        assertThat(resp.status()).as(resp.body()).isEqualTo(201);
        return resp.id();
    }

    protected UUID drive(String token) {
        return drive(token, college(token), List.of(), today.plusDays(1));
    }

    protected Resp addCandidate(String token, UUID drive, String name, String phone) {
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        body.put("phone", phone);
        body.put("email", name.toLowerCase().replace(' ', '.') + "@mail.test");
        body.put("degree", "B.Sc");
        body.put("year", "Final");
        return post(BASE + "/drives/" + drive + "/candidates", token, body);
    }

    protected UUID candidate(String token, UUID drive) {
        Resp resp = addCandidate(token, drive, "Cand " + phone(), phone());
        assertThat(resp.status()).as(resp.body()).isEqualTo(201);
        return resp.id();
    }

    protected Resp outcome(String token, UUID candidate, String outcome) {
        return post(BASE + "/candidates/" + candidate + "/outcome", token, Map.of("outcome", outcome, "note", "ok"));
    }

    protected Resp upload(String token, UUID drive, String csv) {
        String boundary = "----hls" + UUID.randomUUID();
        String crlf = String.valueOf((char) 13) + (char) 10;
        String body = "--" + boundary + crlf
                + "Content-Disposition: form-data; name=\"file\"; filename=\"candidates.csv\"" + crlf
                + "Content-Type: text/plain" + crlf + crlf
                + csv + crlf
                + "--" + boundary + "--" + crlf;
        var spec = client.post()
                .uri(BASE + "/drives/" + drive + "/candidates/import")
                .contentType(MediaType.parseMediaType("multipart/form-data; boundary=" + boundary));
        if (token != null) {
            spec.header("Authorization", "Bearer " + token);
        }
        var result = spec.body(body.getBytes(StandardCharsets.UTF_8)).exchange().returnResult(String.class);
        return new Resp(result.getStatus().value(), result.getResponseBody());
    }

    protected String admin() {
        return signInAs(Role.ADMIN).token();
    }

    protected String directorToken() {
        return signInAs(Role.DIRECTOR).token();
    }

    protected Map<String, Object> offerBody(String salary) {
        Map<String, Object> body = new HashMap<>();
        body.put("role", "Trainee / English Trainer");
        body.put("monthlySalary", salary);
        body.put("allowances", "Travel 1000");
        body.put("terms", "Two months notice");
        body.put("expectedJoining", today.plusDays(30).toString());
        body.put("offerDate", today.toString());
        body.put("responseDeadline", today.plusDays(10).toString());
        return body;
    }

    protected UUID selected(String token, String phone) {
        UUID drive = drive(token);
        Resp added = addCandidate(token, drive, "Cand " + phone, phone);
        assertThat(added.status()).as(added.body()).isEqualTo(201);
        assertThat(outcome(token, added.id(), "SELECTED").status()).isEqualTo(200);
        return added.id();
    }

    protected UUID issued(String director, UUID candidate, String salary) {
        Resp draft = post(BASE + "/candidates/" + candidate + "/offers", director, offerBody(salary));
        assertThat(draft.status()).as(draft.body()).isEqualTo(201);
        Resp issue = post(BASE + "/offers/" + draft.id() + "/issue", director, Map.of());
        assertThat(issue.status()).as(issue.body()).isEqualTo(200);
        return draft.id();
    }
}
