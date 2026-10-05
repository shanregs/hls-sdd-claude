package com.hls.recruitment.marketing;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import com.hls.schoolbilling.SchoolContractsTestBase;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.MediaType;

/** Helpers for the spec 023 integration tests: prospects, activities, uploads and a Zone Manager per Zone. */
public abstract class MarketingTestBase extends SchoolContractsTestBase {

    protected static final String M = "/api/v1/marketing";

    protected String admin() {
        return signInAs(Role.ADMIN).token();
    }

    protected String directorToken() {
        return signInAs(Role.DIRECTOR).token();
    }

    protected Map<String, Object> prospectBody(UUID zone, String name) {
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        body.put("board", "CBSE");
        body.put("address", "5 Lake Road");
        body.put("zoneId", zone);
        body.put("contactPerson", "Mrs Rao");
        body.put("designation", "Principal");
        body.put("phone", "9111111111");
        body.put("email", "rao@school.test");
        body.put("expectedTeachers", 4);
        return body;
    }

    protected UUID prospect(String token, UUID zone) {
        Resp resp = post(M + "/prospects", token, prospectBody(zone, uniqueName("Prospect School")));
        assertThat(resp.status()).as(resp.body()).isEqualTo(201);
        return UUID.fromString((String) ((Map<?, ?>) resp.map().get("row")).get("id"));
    }

    protected Map<String, Object> activityBody(UUID prospect, String type, LocalDate date) {
        Map<String, Object> body = new HashMap<>();
        body.put("prospectId", prospect);
        body.put("type", type);
        body.put("date", date.toString());
        body.put("notes", "Meet the principal");
        return body;
    }

    protected UUID activity(String token, UUID prospect, LocalDate date) {
        Resp resp = post(M + "/activities", token, activityBody(prospect, "VISIT", date));
        assertThat(resp.status()).as(resp.body()).isEqualTo(201);
        return resp.id();
    }

    protected Resp complete(String token, UUID activity, String outcome, LocalDate followUp) {
        Map<String, Object> body = new HashMap<>();
        body.put("outcome", outcome);
        if (followUp != null) {
            body.put("followUpOn", followUp.toString());
        }
        return post(M + "/activities/" + activity + "/complete", token, body);
    }

    /** A multipart upload of one file; the bytes are given as text for the txt cases or as raw bytes. */
    protected Resp upload(String token, UUID activity, String filename, byte[] bytes) {
        String boundary = "----hls" + UUID.randomUUID();
        String crlf = String.valueOf((char) 13) + (char) 10;
        byte[] head = ("--" + boundary + crlf + "Content-Disposition: form-data; name=\"file\"; filename=\"" + filename + "\"" + crlf
                        + "Content-Type: application/octet-stream" + crlf + crlf)
                .getBytes(StandardCharsets.UTF_8);
        byte[] tail = (crlf + "--" + boundary + "--" + crlf).getBytes(StandardCharsets.UTF_8);
        byte[] body = new byte[head.length + bytes.length + tail.length];
        System.arraycopy(head, 0, body, 0, head.length);
        System.arraycopy(bytes, 0, body, head.length, bytes.length);
        System.arraycopy(tail, 0, body, head.length + bytes.length, tail.length);
        var spec = client.post()
                .uri(M + "/activities/" + activity + "/attachments")
                .contentType(MediaType.parseMediaType("multipart/form-data; boundary=" + boundary));
        if (token != null) {
            spec.header("Authorization", "Bearer " + token);
        }
        var result = spec.body(body).exchange().returnResult(String.class);
        return new Resp(result.getStatus().value(), result.getResponseBody());
    }

    protected static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3, 4};

    protected List<Map<String, Object>> rows(Resp resp) {
        return content(resp);
    }
}
