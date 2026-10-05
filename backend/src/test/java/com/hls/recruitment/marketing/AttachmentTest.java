package com.hls.recruitment.marketing;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

/** Photos on a visit: type, size and count limits, download as an attachment, visibility, removal by Admin or Director. */
class AttachmentTest extends MarketingTestBase {

    private UUID visit(String token, UUID zone) {
        return activity(token, prospect(token, zone), today.plusDays(1));
    }

    private UUID fileIdOf(Resp upload) {
        return UUID.fromString((String) upload.map().get("fileId"));
    }

    @Test
    void aPhotoUploadsListsAndDownloadsAsAnAttachmentWithNosniff() {
        String admin = admin();
        UUID visit = visit(admin, zone(admin));

        Resp uploaded = upload(admin, visit, "board photo.png", PNG);

        assertThat(uploaded.status()).as(uploaded.body()).isEqualTo(201);
        assertThat(uploaded.map()).containsEntry("name", "board photo.png").containsEntry("contentType", "image/png");
        assertThat(get(M + "/activities/" + visit, admin).body()).contains("board photo.png");
        var response = client.method(HttpMethod.GET)
                .uri(M + "/files/" + fileIdOf(uploaded))
                .header("Authorization", "Bearer " + admin)
                .exchange()
                .returnResult(byte[].class);
        assertThat(response.getStatus().value()).isEqualTo(200);
        assertThat(response.getResponseHeaders().getFirst("Content-Disposition")).startsWith("attachment");
        assertThat(response.getResponseHeaders().getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(response.getResponseHeaders().getFirst("Content-Type")).isEqualTo("image/png");
        assertThat(response.getResponseBody()).isEqualTo(PNG);
    }

    @Test
    void aWrongTypeAMismatchedContentAndAnOversizeFileAreRefused() {
        String admin = admin();
        UUID visit = visit(admin, zone(admin));

        assertThat(upload(admin, visit, "run.exe", PNG).status()).isEqualTo(400);
        assertThat(upload(admin, visit, "fake.png", "<html>".getBytes(StandardCharsets.UTF_8)).status()).isEqualTo(400);
        byte[] big = new byte[10 * 1024 * 1024 + 1];
        big[0] = '%';
        big[1] = 'P';
        big[2] = 'D';
        big[3] = 'F';
        big[4] = '-';
        Resp tooBig = upload(admin, visit, "big.pdf", big);
        assertThat(tooBig.status()).isEqualTo(400);
        assertThat(tooBig.body()).contains("10 MB");
        assertThat(get(M + "/activities/" + visit, admin).body()).contains("\"attachments\":[]");
    }

    @Test
    void aVisitHoldsAtMostTenFiles() {
        String admin = admin();
        UUID visit = visit(admin, zone(admin));
        for (int i = 0; i < 10; i++) {
            assertThat(upload(admin, visit, "p" + i + ".png", PNG).status()).isEqualTo(201);
        }

        assertThat(upload(admin, visit, "eleventh.png", PNG).status()).isEqualTo(409);
    }

    @Test
    void aCallerWhoCannotSeeTheVisitGetsNotFoundForTheFile() {
        String admin = admin();
        UUID zoneA = zone(admin);
        UUID zoneB = zone(admin);
        ManagerCtx managerB = newManager(admin, zoneB);
        ManagerCtx managerA = newManager(admin, zoneA);
        UUID visit = visit(admin, zoneA);
        UUID file = fileIdOf(upload(admin, visit, "p.png", PNG));

        assertThat(get(M + "/files/" + file, managerA.token()).status()).isEqualTo(200);
        assertThat(get(M + "/files/" + file, managerB.token()).status()).isEqualTo(404);
        assertThat(upload(managerB.token(), visit, "x.png", PNG).status()).isEqualTo(404);
        assertThat(get(M + "/files/" + file, signInAs(Role.TEACHER).token()).status()).isEqualTo(403);
        assertThat(get(M + "/files/" + UUID.randomUUID(), admin).status()).isEqualTo(404);
    }

    @Test
    void onlyAdminAndDirectorRemoveAFileWithAReasonAndTheBytesAreKept() {
        String admin = admin();
        UUID zone = zone(admin);
        ManagerCtx manager = newManager(admin, zone);
        UUID visit = visit(admin, zone);
        UUID file = fileIdOf(upload(manager.token(), visit, "p.png", PNG));
        String url = M + "/activities/" + visit + "/attachments/" + file;

        assertThat(send(HttpMethod.DELETE, url, manager.token(), Map.of("reason", "wrong")).status()).isEqualTo(403);
        assertThat(send(HttpMethod.DELETE, url, directorToken(), Map.of("reason", " ")).status()).isEqualTo(400);
        assertThat(send(HttpMethod.DELETE, url, directorToken(), Map.of("reason", "Wrong visit")).status()).isEqualTo(204);

        assertThat(get(M + "/files/" + file, admin).status()).isEqualTo(404);
        assertThat(get(M + "/activities/" + visit, admin).body()).contains("\"attachments\":[]");
        Integer kept = jdbc.queryForObject("select count(*) from stored_file where id = ? and removal_reason = 'Wrong visit'", Integer.class, file);
        assertThat(kept).isEqualTo(1);
        assertChangeRecorded(admin, "ACTIVITY_ATTACHMENT", visit, "removed");
    }
}
