package com.hls.content;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import com.hls.support.MasterDataTestBase;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Spec 029 T022: the content lifecycle, the reviewer rules, the optional task, visibility and file rules through the API. */
class ContentLifecycleTest extends MasterDataTestBase {

    private static final String BASE = "/api/v1/content";

    private static byte[] mp4() {
        byte[] bytes = new byte[2048];
        byte[] head = {0, 0, 0, 0x18, 'f', 't', 'y', 'p', 'i', 's', 'o', 'm', 0, 0, 2, 0};
        System.arraycopy(head, 0, bytes, 0, head.length);
        return bytes;
    }

    private static byte[] pdf() {
        return "%PDF-1.4 content body".getBytes();
    }

    private static LocalDate today() {
        return LocalDate.now(ZoneId.of("Asia/Kolkata"));
    }

    private Resp upload(String token, UUID id, String fileName, byte[] bytes, String note) throws Exception {
        String boundary = "----hls" + UUID.randomUUID();
        StringBuilder head = new StringBuilder();
        if (fileName != null) {
            head.append("--").append(boundary).append("\r\nContent-Disposition: form-data; name=\"file\"; filename=\"")
                    .append(fileName).append("\"\r\nContent-Type: application/octet-stream\r\n\r\n");
        }
        byte[] headBytes = head.toString().getBytes();
        byte[] payload = fileName == null ? new byte[0] : bytes;
        StringBuilder tail = new StringBuilder();
        if (fileName != null) {
            tail.append("\r\n");
        }
        if (note != null) {
            tail.append("--").append(boundary).append("\r\nContent-Disposition: form-data; name=\"note\"\r\n\r\n").append(note).append("\r\n");
        }
        tail.append("--").append(boundary).append("--\r\n");
        byte[] tailBytes = tail.toString().getBytes();
        byte[] body = new byte[headBytes.length + payload.length + tailBytes.length];
        System.arraycopy(headBytes, 0, body, 0, headBytes.length);
        System.arraycopy(payload, 0, body, headBytes.length, payload.length);
        System.arraycopy(tailBytes, 0, body, headBytes.length + payload.length, tailBytes.length);
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + BASE + "/" + id + "/versions"))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();
        HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        return new Resp(response.statusCode(), response.body());
    }

    private Resp download(String token, UUID id, int number) {
        return get(BASE + "/" + id + "/versions/" + number + "/file", token);
    }

    private Map<String, Object> body(String title, UUID author, LocalDate due) {
        Map<String, Object> body = new HashMap<>();
        body.put("title", title);
        body.put("category", "Lesson");
        body.put("subject", "English");
        body.put("description", "Phonics week");
        if (author != null) {
            body.put("authorUserId", author);
        }
        if (due != null) {
            body.put("dueOn", due.toString());
        }
        return body;
    }

    private UUID newContent(String token, UUID author, LocalDate due) {
        Resp resp = post(BASE, token, body(uniqueName("Content"), author, due));
        assertThat(resp.status()).as(resp.body()).isEqualTo(201);
        return resp.id();
    }

    private String statusOf(String token, UUID id) {
        return (String) get(BASE + "/" + id, token).map().get("status");
    }

    private Resp submitOk(String token, UUID id) throws Exception {
        Resp resp = upload(token, id, "lesson.mp4", mp4(), "first cut");
        assertThat(resp.status()).as(resp.body()).isEqualTo(201);
        return resp;
    }

    private String taskStatus(UUID content) {
        return jdbc.queryForObject("select status from task where source = 'CONTENT' and source_key = ?", String.class, content.toString());
    }

    private int taskCount(UUID content) {
        return jdbc.queryForObject("select count(*) from task where source = 'CONTENT' and source_key = ?", Integer.class, content.toString());
    }

    private int notifications(UUID user, String type) {
        return jdbc.queryForObject("select count(*) from notification where recipient_user_id = ? and type = ?", Integer.class, user, type);
    }

    @Test
    void contributorSubmitsDirectorReturnsThenApprovesAndAllVersionsStay() throws Exception {
        Signed contributor = signInAs(Role.CONTENT_CONTRIBUTOR);
        Signed director = signInAs(Role.DIRECTOR);
        UUID id = newContent(contributor.token(), null, null);
        assertThat(statusOf(contributor.token(), id)).isEqualTo("DRAFT");
        assertThat(taskCount(id)).isZero();

        submitOk(contributor.token(), id);
        assertThat(statusOf(director.token(), id)).isEqualTo("SUBMITTED");
        assertThat(notifications(director.userId(), "CONTENT_SUBMITTED")).isGreaterThanOrEqualTo(1);

        Resp noComment = post(BASE + "/" + id + "/return", director.token(), Map.of("comment", " "));
        assertThat(noComment.status()).isEqualTo(400);
        Resp returned = post(BASE + "/" + id + "/return", director.token(), Map.of("comment", "Audio is too quiet"));
        assertThat(returned.status()).as(returned.body()).isEqualTo(200);
        assertThat(returned.map().get("status")).isEqualTo("RETURNED");
        assertThat(notifications(contributor.userId(), "CONTENT_RETURNED")).isEqualTo(1);

        assertThat(upload(contributor.token(), id, "lesson-v2.pdf", pdf(), null).status()).isEqualTo(201);
        Resp approved = post(BASE + "/" + id + "/approve", director.token(), null);
        assertThat(approved.status()).as(approved.body()).isEqualTo(200);
        Map<String, Object> detail = approved.map();
        assertThat(detail.get("status")).isEqualTo("APPROVED");
        assertThat(detail.get("currentVersion")).isEqualTo(2);
        assertThat(detail.get("lastApprovedVersion")).isEqualTo(2);
        assertThat((List<?>) detail.get("versions")).hasSize(2);
        assertThat((List<?>) detail.get("reviews")).hasSize(2);
        assertThat(notifications(contributor.userId(), "CONTENT_APPROVED")).isEqualTo(1);
        assertThat(download(contributor.token(), id, 1).status()).isEqualTo(200);
        assertThat(download(contributor.token(), id, 2).status()).isEqualTo(200);
    }

    @Test
    void submittingWithoutAFileIsRefusedAndNothingChanges() throws Exception {
        Signed contributor = signInAs(Role.CONTENT_CONTRIBUTOR);
        UUID id = newContent(contributor.token(), null, null);
        Resp none = upload(contributor.token(), id, null, null, "just a note");
        assertThat(none.status()).isEqualTo(400);
        assertThat(statusOf(contributor.token(), id)).isEqualTo("DRAFT");
    }

    @Test
    void wrongTypesWrongBytesAndOversizeFilesAreRefused() throws Exception {
        Signed contributor = signInAs(Role.CONTENT_CONTRIBUTOR);
        UUID id = newContent(contributor.token(), null, null);
        assertThat(upload(contributor.token(), id, "old.doc", pdf(), null).status()).isEqualTo(400);
        assertThat(upload(contributor.token(), id, "run.exe", pdf(), null).status()).isEqualTo(400);
        assertThat(upload(contributor.token(), id, "fake.mp4", pdf(), null).status()).isEqualTo(400);
        byte[] big = new byte[26 * 1024 * 1024];
        System.arraycopy(pdf(), 0, big, 0, pdf().length);
        Resp tooBig = upload(contributor.token(), id, "big.pdf", big, null);
        assertThat(tooBig.status()).isEqualTo(400);
        assertThat(tooBig.body()).contains("25 MB");
        assertThat(statusOf(contributor.token(), id)).isEqualTo("DRAFT");
    }

    @Test
    void theAuthorAndTheSubmitterCanNeverReviewEvenWithApprove() throws Exception {
        Signed director = signInAs(Role.DIRECTOR);
        Signed otherDirector = signInAs(Role.DIRECTOR);
        UUID id = newContent(director.token(), null, null);
        submitOk(director.token(), id);
        Resp self = post(BASE + "/" + id + "/approve", director.token(), null);
        assertThat(self.status()).isEqualTo(403);
        Resp selfReturn = post(BASE + "/" + id + "/return", director.token(), Map.of("comment", "I disagree with myself"));
        assertThat(selfReturn.status()).isEqualTo(403);
        assertThat(((Map<?, ?>) get(BASE + "/" + id, director.token()).map().get("actions")).get("approve")).isEqualTo(false);
        assertThat(((Map<?, ?>) get(BASE + "/" + id, otherDirector.token()).map().get("actions")).get("approve")).isEqualTo(true);
        assertThat(post(BASE + "/" + id + "/approve", otherDirector.token(), null).status()).isEqualTo(200);
    }

    @Test
    void aSubmitterOnBehalfOfTheAuthorCannotReviewEither() throws Exception {
        Signed admin = signInAs(Role.ADMIN);
        Signed director = signInAs(Role.DIRECTOR);
        Signed contributor = signInAs(Role.CONTENT_CONTRIBUTOR);
        UUID id = newContent(admin.token(), contributor.userId(), null);
        assertThat(upload(admin.token(), id, "lesson.mp4", mp4(), null).status()).isEqualTo(201);
        Map<String, Object> detail = get(BASE + "/" + id, director.token()).map();
        Map<?, ?> version = (Map<?, ?>) ((List<?>) detail.get("versions")).get(0);
        assertThat(version.get("authorUserId")).isEqualTo(contributor.userId().toString());
        assertThat(version.get("submittedByUserId")).isEqualTo(admin.userId().toString());
        assertThat(post(BASE + "/" + id + "/approve", admin.token(), null).status()).isIn(403);
        assertThat(post(BASE + "/" + id + "/approve", director.token(), null).status()).isEqualTo(200);
    }

    @Test
    void contentWithNoOtherReviewerWaitsAndAdminsAndDirectorsAreToldOncePerVersion() throws Exception {
        Signed director = signInAs(Role.DIRECTOR);
        Signed admin = signInAs(Role.ADMIN);
        List<UUID> others = jdbc.queryForList(
                "select a.id from app_user a join role_assignment r on r.user_id = a.id where r.role = 'DIRECTOR' and a.active = true and a.id <> ?",
                UUID.class,
                director.userId());
        for (UUID other : others) {
            jdbc.update("update app_user set active = false where id = ?", other);
        }
        try {
            UUID id = newContent(director.token(), null, null);
            submitOk(director.token(), id);
            assertThat(statusOf(director.token(), id)).isEqualTo("SUBMITTED");
            assertThat(notifications(admin.userId(), "CONTENT_NO_REVIEWER")).isEqualTo(1);
            assertThat(notifications(director.userId(), "CONTENT_NO_REVIEWER")).isEqualTo(1);
            // the second version is a new notice, but the same version never repeats
            assertThat(post(BASE + "/" + id + "/approve", admin.token(), null).status()).isEqualTo(403);
        } finally {
            for (UUID other : others) {
                jdbc.update("update app_user set active = true where id = ?", other);
            }
        }
    }

    @Test
    void adminGrantedApproveCanApproveDirectorContent() throws Exception {
        Signed director = signInAs(Role.DIRECTOR);
        Signed admin = signInAs(Role.ADMIN);
        UUID id = newContent(director.token(), null, null);
        submitOk(director.token(), id);
        assertThat(post(BASE + "/" + id + "/approve", admin.token(), null).status()).isEqualTo(403);
        assertThat(put("/api/v1/identity/permission-matrix/ADMIN/CONTENT/APPROVE", admin.token(), Map.of("granted", true)).status()).isEqualTo(200);
        try {
            assertThat(post(BASE + "/" + id + "/approve", admin.token(), null).status()).isEqualTo(200);
        } finally {
            put("/api/v1/identity/permission-matrix/ADMIN/CONTENT/APPROVE", admin.token(), Map.of("granted", false));
        }
    }

    @Test
    void newVersionOfApprovedContentGoesBackToReviewAndKeepsTheLastApprovedVersion() throws Exception {
        Signed contributor = signInAs(Role.CONTENT_CONTRIBUTOR);
        Signed director = signInAs(Role.DIRECTOR);
        UUID id = newContent(contributor.token(), null, null);
        submitOk(contributor.token(), id);
        assertThat(post(BASE + "/" + id + "/approve", director.token(), null).status()).isEqualTo(200);
        assertThat(upload(contributor.token(), id, "lesson-v2.mp4", mp4(), null).status()).isEqualTo(201);
        Map<String, Object> detail = get(BASE + "/" + id, director.token()).map();
        assertThat(detail.get("status")).isEqualTo("SUBMITTED");
        assertThat(detail.get("currentVersion")).isEqualTo(2);
        assertThat(detail.get("lastApprovedVersion")).isEqualTo(1);
        // submitting again while it waits for review is refused
        assertThat(upload(contributor.token(), id, "lesson-v3.mp4", mp4(), null).status()).isEqualTo(409);
    }

    @Test
    void aTaskExistsOnlyForAnActiveManagerAuthorWithADueDateAndFollowsTheContent() throws Exception {
        Signed admin = signInAs(Role.ADMIN);
        Signed director = signInAs(Role.DIRECTOR);
        ManagerCtx manager = newManager(admin.token());
        Signed contributor = signInAs(Role.CONTENT_CONTRIBUTOR);

        UUID noDue = newContent(director.token(), manager.signed().userId(), null);
        UUID forContributor = newContent(director.token(), contributor.userId(), today().plusDays(3));
        assertThat(taskCount(noDue)).isZero();
        assertThat(taskCount(forContributor)).isZero();

        UUID id = newContent(director.token(), manager.signed().userId(), today().plusDays(3));
        assertThat(taskCount(id)).isEqualTo(1);
        assertThat(taskStatus(id)).isEqualTo("ASSIGNED");
        assertThat(notifications(manager.signed().userId(), "CONTENT_ASSIGNED")).isGreaterThanOrEqualTo(2);

        assertThat(upload(manager.token(), id, "lesson.mp4", mp4(), null).status()).isEqualTo(201);
        assertThat(taskStatus(id)).isEqualTo("COMPLETED");
        assertThat(post(BASE + "/" + id + "/return", director.token(), Map.of("comment", "Needs a clearer intro")).status()).isEqualTo(200);
        assertThat(taskStatus(id)).isEqualTo("IN_PROGRESS");
        assertThat(upload(manager.token(), id, "lesson2.mp4", mp4(), null).status()).isEqualTo(201);
        assertThat(taskStatus(id)).isEqualTo("COMPLETED");
        assertThat(post(BASE + "/" + id + "/approve", director.token(), null).status()).isEqualTo(200);
        assertThat(taskStatus(id)).isEqualTo("VERIFIED");
    }

    @Test
    void contentWithoutATaskIsSubmittedAndReviewedWithNoTaskAtAll() throws Exception {
        Signed contributor = signInAs(Role.CONTENT_CONTRIBUTOR);
        Signed director = signInAs(Role.DIRECTOR);
        UUID id = newContent(contributor.token(), null, today().minusDays(1));
        submitOk(contributor.token(), id);
        assertThat(post(BASE + "/" + id + "/approve", director.token(), null).status()).isEqualTo(200);
        assertThat(taskCount(id)).isZero();
    }

    @Test
    void visibilityIsAuthoredOrSubmittedForOthersAndEverythingForAdminAndDirector() throws Exception {
        Signed one = signInAs(Role.CONTENT_CONTRIBUTOR);
        Signed two = signInAs(Role.CONTENT_CONTRIBUTOR);
        Signed manager = signInAs(Role.MANAGER);
        Signed director = signInAs(Role.DIRECTOR);
        Signed admin = signInAs(Role.ADMIN);
        UUID id = newContent(one.token(), null, null);
        assertThat(get(BASE + "/" + id, two.token()).status()).isEqualTo(404);
        assertThat(get(BASE + "/" + id, manager.token()).status()).isEqualTo(404);
        assertThat(upload(two.token(), id, "x.mp4", mp4(), null).status()).isEqualTo(404);
        assertThat(post(BASE + "/" + id + "/approve", two.token(), null).status()).isEqualTo(403);
        assertThat(get(BASE + "/" + id, director.token()).status()).isEqualTo(200);
        assertThat(get(BASE + "/" + id, admin.token()).status()).isEqualTo(200);
        List<String> theirs = content(get(BASE, one.token())).stream().map(m -> (String) m.get("id")).toList();
        assertThat(theirs).contains(id.toString());
        List<String> notTheirs = content(get(BASE, two.token())).stream().map(m -> (String) m.get("id")).toList();
        assertThat(notTheirs).doesNotContain(id.toString());
        assertThat(download(two.token(), id, 1).status()).isEqualTo(404);
    }

    @Test
    void onlyAdminOrDirectorCreatesForAnotherAuthorAndTheAuthorMustBeAbleToCreate() {
        Signed contributor = signInAs(Role.CONTENT_CONTRIBUTOR);
        Signed other = signInAs(Role.CONTENT_CONTRIBUTOR);
        Signed teacher = signInAs(Role.TEACHER);
        Signed admin = signInAs(Role.ADMIN);
        assertThat(post(BASE, contributor.token(), body("For someone else", other.userId(), null)).status()).isEqualTo(403);
        assertThat(post(BASE, admin.token(), body("For a teacher", teacher.userId(), null)).status()).isEqualTo(400);
        assertThat(post(BASE, admin.token(), body("For a contributor", other.userId(), null)).status()).isEqualTo(201);
        assertThat(post(BASE, contributor.token(), body("ab", null, null)).status()).isEqualTo(400);
    }

    @Test
    void aStaleEditGets409AndAnEditChangesTheRecord() {
        Signed contributor = signInAs(Role.CONTENT_CONTRIBUTOR);
        UUID id = newContent(contributor.token(), null, null);
        Map<String, Object> detail = get(BASE + "/" + id, contributor.token()).map();
        long version = ((Number) detail.get("version")).longValue();
        Map<String, Object> edit = body("A new title", null, null);
        edit.put("version", version);
        assertThat(put(BASE + "/" + id, contributor.token(), edit).status()).isEqualTo(200);
        assertThat(put(BASE + "/" + id, contributor.token(), edit).status()).isEqualTo(409);
        assertThat(get(BASE + "/" + id, contributor.token()).map().get("title")).isEqualTo("A new title");
    }

    @Test
    void onlyAdminOrDirectorRemovesAFileVersionWithAReasonAndTheRowStays() throws Exception {
        Signed contributor = signInAs(Role.CONTENT_CONTRIBUTOR);
        Signed admin = signInAs(Role.ADMIN);
        UUID id = newContent(contributor.token(), null, null);
        submitOk(contributor.token(), id);
        String uri = BASE + "/" + id + "/versions/1/file";
        assertThat(send(org.springframework.http.HttpMethod.DELETE, uri, contributor.token(), Map.of("reason", "Please remove")).status()).isEqualTo(403);
        Resp removed = send(org.springframework.http.HttpMethod.DELETE, uri, admin.token(), Map.of("reason", "Wrong recording"));
        assertThat(removed.status()).as(removed.body()).isEqualTo(200);
        assertThat(download(contributor.token(), id, 1).status()).isEqualTo(404);
        Map<?, ?> version = (Map<?, ?>) ((List<?>) removed.map().get("versions")).get(0);
        assertThat(version.get("removed")).isEqualTo(true);
        assertThat(version.get("removalReason")).isEqualTo("Wrong recording");
    }

    @Test
    void everyEndpointRefusesTeacherAndSystemAndTheContributorCannotApprove() {
        Signed contributor = signInAs(Role.CONTENT_CONTRIBUTOR);
        UUID id = newContent(contributor.token(), null, null);
        for (Role role : List.of(Role.TEACHER, Role.SYSTEM)) {
            String token = signInAs(role).token();
            assertThat(get(BASE, token).status()).as(role + " list").isEqualTo(403);
            assertThat(get(BASE + "/summary", token).status()).as(role + " summary").isEqualTo(403);
            assertThat(get(BASE + "/authors", token).status()).as(role + " authors").isEqualTo(403);
            assertThat(get(BASE + "/limits", token).status()).as(role + " limits").isEqualTo(403);
            assertThat(get(BASE + "/" + id, token).status()).as(role + " detail").isEqualTo(403);
            assertThat(post(BASE, token, body("Not allowed", null, null)).status()).as(role + " create").isEqualTo(403);
            assertThat(post(BASE + "/" + id + "/approve", token, null).status()).as(role + " approve").isEqualTo(403);
            assertThat(post(BASE + "/" + id + "/return", token, Map.of("comment", "no")).status()).as(role + " return").isEqualTo(403);
        }
        assertThat(post(BASE + "/" + id + "/approve", contributor.token(), null).status()).isEqualTo(403);
        assertThat(get(BASE + "/limits", contributor.token()).status()).isEqualTo(200);
        assertThat(get(BASE + "/authors", contributor.token()).status()).isEqualTo(200);
    }

    @Test
    void theLimitsEndpointListsEveryAcceptedTypeWithItsLimit() {
        Signed contributor = signInAs(Role.CONTENT_CONTRIBUTOR);
        Resp resp = get(BASE + "/limits", contributor.token());
        assertThat(resp.status()).isEqualTo(200);
        assertThat(resp.body()).contains("\"mp4\"").contains("524288000").contains("\"wav\"").contains("\"docx\"").contains("26214400");
    }

    @Test
    void historyKeepsEveryStepWithActorAndTime() throws Exception {
        Signed contributor = signInAs(Role.CONTENT_CONTRIBUTOR);
        Signed director = signInAs(Role.DIRECTOR);
        UUID id = newContent(contributor.token(), null, null);
        submitOk(contributor.token(), id);
        post(BASE + "/" + id + "/approve", director.token(), null);
        List<?> history = (List<?>) get(BASE + "/" + id, director.token()).map().get("history");
        List<String> events = history.stream().map(h -> (String) ((Map<?, ?>) h).get("event")).toList();
        assertThat(events).contains("CREATED", "SUBMITTED", "VERSION_ADDED", "APPROVED");
        int rows = jdbc.queryForObject("select count(*) from content_history where content_id = ?", Integer.class, id);
        assertThat(rows).isEqualTo(history.size());
    }
}
