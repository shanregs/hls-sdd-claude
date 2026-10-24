package com.hls.schoolbilling;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;

/** Helpers for the spec 031 tests: a School with an ACTIVE MoU, uploads, and the approval calls. */
public abstract class MouTestBase extends SchoolContractsTestBase {

    protected static final byte[] PDF = "%PDF-1.4\n1 0 obj\n<<>>\nendobj\n".getBytes(StandardCharsets.UTF_8);
    protected static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3, 4};

    /** A School with a Zone Manager and one ACTIVE MoU (DRAFT approval) recorded by Admin. */
    protected record Mou(Fixture f, String admin, Signed director, UUID contractId) {
        String managerToken() {
            return f.manager().token();
        }
    }

    protected Mou mou() {
        String admin = signInAs(Role.ADMIN).token();
        Fixture f = fixture(admin);
        Signed dir = director();
        UUID id = contractId(createContract(admin, f.schoolId(), sameSalaryBody(f, dir.userId(), 2, "15000")));
        return new Mou(f, admin, dir, id);
    }

    protected String approvalUrl(UUID contractId) {
        return BASE + "/contracts/" + contractId + "/approval";
    }

    protected Resp approval(String token, UUID contractId) {
        return get(approvalUrl(contractId), token);
    }

    protected long versionOf(String token, UUID contractId) {
        Resp resp = approval(token, contractId);
        assertThat(resp.status()).as(resp.body()).isEqualTo(200);
        return ((Number) resp.map().get("version")).longValue();
    }

    protected String stateOf(String token, UUID contractId) {
        return (String) approval(token, contractId).map().get("state");
    }

    protected Resp submit(String token, UUID contractId) {
        return post(BASE + "/contracts/" + contractId + "/submit", token, Map.of("version", versionOf(token, contractId)));
    }

    protected Resp approve(String token, UUID contractId) {
        return post(BASE + "/contracts/" + contractId + "/approve", token, Map.of("version", versionOf(token, contractId)));
    }

    protected Resp reject(String token, UUID contractId, String reason) {
        return post(
                BASE + "/contracts/" + contractId + "/reject",
                token,
                Map.of("version", versionOf(token, contractId), "reason", reason));
    }

    protected Resp patchTerms(String token, UUID contractId, Map<String, Object> body) {
        return send(HttpMethod.PATCH, BASE + "/contracts/" + contractId + "/terms", token, body);
    }

    protected Resp removeDocument(String token, UUID contractId, UUID documentId, String reason) {
        return post(
                BASE + "/contracts/" + contractId + "/documents/" + documentId + "/remove",
                token,
                reason == null ? Map.of() : Map.of("reason", reason));
    }

    /** A multipart upload of one file. */
    protected Resp upload(String token, UUID contractId, String filename, byte[] bytes) {
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
                .uri(BASE + "/contracts/" + contractId + "/documents")
                .contentType(MediaType.parseMediaType("multipart/form-data; boundary=" + boundary));
        if (token != null) {
            spec.header("Authorization", "Bearer " + token);
        }
        var result = spec.body(body).exchange().returnResult(String.class);
        return new Resp(result.getStatus().value(), result.getResponseBody());
    }

    protected Resp download(String token, UUID contractId, Object documentId) {
        return get(BASE + "/contracts/" + contractId + "/documents/" + documentId + "/file", token);
    }

    @SuppressWarnings("unchecked")
    protected static List<Map<String, Object>> documentsOf(Resp approval) {
        return (List<Map<String, Object>>) approval.map().get("documents");
    }

    @SuppressWarnings("unchecked")
    protected static List<Map<String, Object>> historyOf(Resp approval) {
        return (List<Map<String, Object>>) approval.map().get("history");
    }

    @SuppressWarnings("unchecked")
    protected static List<String> actionsOf(Resp approval) {
        return (List<String>) approval.map().get("actions");
    }

    protected static List<String> historyActions(Resp approval) {
        return historyOf(approval).stream().map(h -> (String) h.get("action")).toList();
    }

    protected UUID firstDocumentId(Resp approval) {
        return UUID.fromString((String) documentsOf(approval).get(0).get("id"));
    }

    /** Grants or revokes a matrix cell as Admin; the caller restores it. */
    protected void setGrant(String adminToken, Role role, String module, String action, boolean granted) {
        Resp resp = put(
                "/api/v1/identity/permission-matrix/" + role + "/" + module + "/" + action,
                adminToken,
                Map.of("granted", granted));
        assertThat(resp.status()).as(resp.body()).isEqualTo(200);
    }
}
