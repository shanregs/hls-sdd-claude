package com.hls.content;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import com.hls.support.MasterDataTestBase;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Spec 029 T026: the Content dashboard tiles equal the Content list counts, the inbox lists what a reviewer may decide, and the list filters work. */
class ContentTilesTest extends MasterDataTestBase {

    private static final String BASE = "/api/v1/content";
    private static final String AREA = "/api/v1/dashboard/areas/CONTENT";

    private static byte[] pdf() {
        return "%PDF-1.4 tile test".getBytes();
    }

    private UUID newContent(String token, String category, String subject) {
        Map<String, Object> body = new HashMap<>();
        body.put("title", uniqueName("Tile content"));
        body.put("category", category);
        body.put("subject", subject);
        Resp resp = post(BASE, token, body);
        assertThat(resp.status()).as(resp.body()).isEqualTo(201);
        return resp.id();
    }

    private void submit(String token, UUID id) throws Exception {
        String boundary = "----hls" + UUID.randomUUID();
        byte[] head = ("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"a.pdf\"\r\nContent-Type: application/pdf\r\n\r\n").getBytes();
        byte[] tail = ("\r\n--" + boundary + "--\r\n").getBytes();
        byte[] file = pdf();
        byte[] body = new byte[head.length + file.length + tail.length];
        System.arraycopy(head, 0, body, 0, head.length);
        System.arraycopy(file, 0, body, head.length, file.length);
        System.arraycopy(tail, 0, body, head.length + file.length, tail.length);
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + BASE + "/" + id + "/versions"))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();
        HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
    }

    @SuppressWarnings("unchecked")
    private long figure(String token, String suffix) {
        Resp tile = get(AREA + "/tiles/content." + suffix, token);
        assertThat(tile.status()).as(tile.body()).isEqualTo(200);
        assertThat(tile.map().get("state")).isEqualTo("OK");
        List<Map<String, Object>> values = (List<Map<String, Object>>) tile.map().get("values");
        return ((Number) values.get(0).get("value")).longValue();
    }

    private long listed(String token, String status) {
        return total(get(BASE + "?size=1" + (status == null ? "" : "&status=" + status), token));
    }

    @SuppressWarnings("unchecked")
    private static List<String> inboxIds(Resp resp) {
        return ((List<Map<String, Object>>) resp.map().get("items")).stream().map(i -> (String) i.get("itemId")).toList();
    }

    @Test
    void everyTileEqualsTheContentListForItsStatus() throws Exception {
        Signed contributor = signInAs(Role.CONTENT_CONTRIBUTOR);
        Signed director = signInAs(Role.DIRECTOR);
        UUID waiting = newContent(contributor.token(), "Lesson", "English");
        submit(contributor.token(), waiting);
        UUID approved = newContent(contributor.token(), "Lesson", "Maths");
        submit(contributor.token(), approved);
        assertThat(post(BASE + "/" + approved + "/approve", director.token(), null).status()).isEqualTo(200);

        assertThat(figure(contributor.token(), "awaiting")).isEqualTo(listed(contributor.token(), "SUBMITTED")).isEqualTo(1);
        assertThat(figure(contributor.token(), "approved")).isEqualTo(listed(contributor.token(), "APPROVED")).isEqualTo(1);
        assertThat(figure(contributor.token(), "submitted")).isEqualTo(2);
        assertThat(figure(director.token(), "awaiting")).isEqualTo(listed(director.token(), "SUBMITTED"));
        assertThat(figure(director.token(), "approved")).isEqualTo(listed(director.token(), "APPROVED"));
    }

    @Test
    void theOverdueTileCountsOnlyContentWithAPastDueDateAndNothingSubmitted() throws Exception {
        Signed contributor = signInAs(Role.CONTENT_CONTRIBUTOR);
        Map<String, Object> late = new HashMap<>();
        late.put("title", uniqueName("Late content"));
        late.put("category", "Lesson");
        late.put("subject", "English");
        late.put("dueOn", java.time.LocalDate.now().minusDays(3).toString());
        assertThat(post(BASE, contributor.token(), late).status()).isEqualTo(201);
        newContent(contributor.token(), "Lesson", "English");
        assertThat(figure(contributor.token(), "overdue")).isEqualTo(1);
    }

    @Test
    void theInboxListsSubmittedContentForAReviewerExceptWhatTheyWroteOrSubmitted() throws Exception {
        Signed contributor = signInAs(Role.CONTENT_CONTRIBUTOR);
        Signed director = signInAs(Role.DIRECTOR);
        UUID theirs = newContent(director.token(), "Lesson", "English");
        submit(director.token(), theirs);
        UUID others = newContent(contributor.token(), "Lesson", "English");
        submit(contributor.token(), others);
        String inbox = "/api/v1/dashboard/inbox?kind=CONTENT_REVIEW&size=100";
        Resp mine = get(inbox, director.token());
        assertThat(mine.status()).as(mine.body()).isEqualTo(200);
        assertThat(inboxIds(mine)).contains(others.toString()).doesNotContain(theirs.toString());
        assertThat(post(BASE + "/" + others + "/approve", director.token(), null).status()).isEqualTo(200);
        assertThat(inboxIds(get(inbox, director.token()))).doesNotContain(others.toString());
    }

    @Test
    void theTilesAreNotOfferedToTeacherOrSystem() {
        for (Role role : List.of(Role.TEACHER, Role.SYSTEM)) {
            String token = signInAs(role).token();
            assertThat(get(AREA + "/tiles/content.awaiting", token).status()).as(role.name()).isIn(403, 404);
        }
    }

    @Test
    void theListFiltersByAuthorCategorySubjectStatusAndDates() throws Exception {
        Signed one = signInAs(Role.CONTENT_CONTRIBUTOR);
        Signed two = signInAs(Role.CONTENT_CONTRIBUTOR);
        Signed director = signInAs(Role.DIRECTOR);
        String category = uniqueName("Cat").replace(' ', '-');
        UUID a = newContent(one.token(), category, "English");
        UUID b = newContent(two.token(), category, "Maths");
        submit(two.token(), b);
        assertThat(ids(get(BASE + "?size=100&category=" + category, director.token()))).containsExactlyInAnyOrder(a.toString(), b.toString());
        assertThat(ids(get(BASE + "?size=100&category=" + category + "&authorUserId=" + two.userId(), director.token()))).containsExactly(b.toString());
        assertThat(ids(get(BASE + "?size=100&category=" + category + "&subject=Eng", director.token()))).containsExactly(a.toString());
        assertThat(ids(get(BASE + "?size=100&category=" + category + "&status=SUBMITTED", director.token()))).containsExactly(b.toString());
        assertThat(ids(get(BASE + "?size=100&category=" + category + "&status=DRAFT", director.token()))).containsExactly(a.toString());
        assertThat(get(BASE + "?status=NOPE", director.token()).status()).isEqualTo(400);
        String today = java.time.LocalDate.now().toString();
        assertThat(ids(get(BASE + "?size=100&category=" + category + "&from=" + today + "&to=" + today, director.token()))).hasSize(2);
        assertThat(ids(get(BASE + "?size=100&category=" + category + "&to=2000-01-01", director.token()))).isEmpty();
    }

    private static List<String> ids(Resp list) {
        assertThat(list.status()).as(list.body()).isEqualTo(200);
        return content(list).stream().map(m -> (String) m.get("id")).toList();
    }
}
