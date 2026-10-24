package com.hls.schoolbilling;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

/** Spec 031 US1: attach, replace, download and remove the signed document, by role. */
class MouDocumentApiTest extends MouTestBase {

    @Test
    void adminAndDirectorUploadVersionsAndTheLatestIsCurrent() {
        Mou m = mou();
        String dir = signIn(m.director().userId(), m.director().phone(), PASSWORD).token();

        Resp first = upload(m.admin(), m.contractId(), "mou-signed.pdf", PDF);
        Resp second = upload(dir, m.contractId(), "mou-corrected.pdf", PDF);

        assertThat(first.status()).as(first.body()).isEqualTo(201);
        assertThat(second.status()).as(second.body()).isEqualTo(201);
        assertThat(second.map()).containsEntry("state", "DRAFT");
        var docs = documentsOf(second);
        assertThat(docs).hasSize(2);
        assertThat(docs.get(0))
                .containsEntry("versionNo", 2)
                .containsEntry("current", true)
                .containsEntry("name", "mou-corrected.pdf")
                .containsEntry("contentType", "application/pdf");
        assertThat(docs.get(1)).containsEntry("versionNo", 1).containsEntry("current", false);
        assertThat(historyActions(second)).containsExactly("DOCUMENT_ADDED", "DOCUMENT_REPLACED");
    }

    @Test
    void aManagerHoldingViewCannotUploadRemoveOrSubmitAndEvenGrantedEditStaysRefused() {
        Mou m = mou();
        Resp uploaded = upload(m.admin(), m.contractId(), "a.pdf", PDF);
        UUID doc = firstDocumentId(uploaded);
        String manager = m.managerToken();

        assertThat(upload(manager, m.contractId(), "b.pdf", PDF).status()).isEqualTo(403);
        assertThat(removeDocument(manager, m.contractId(), doc, "x").status()).isEqualTo(403);
        assertThat(post(BASE + "/contracts/" + m.contractId() + "/submit", manager, Map.of("version", 0)).status())
                .isEqualTo(403);

        setGrant(m.admin(), Role.MANAGER, "SCHOOL_CONTRACTS", "EDIT", true);
        try {
            assertThat(upload(manager, m.contractId(), "b.pdf", PDF).status()).isEqualTo(403);
            assertThat(removeDocument(manager, m.contractId(), doc, "x").status()).isEqualTo(403);
            assertThat(post(
                                    BASE + "/contracts/" + m.contractId() + "/submit",
                                    manager,
                                    Map.of("version", versionOf(m.admin(), m.contractId())))
                            .status())
                    .isEqualTo(403);
        } finally {
            setGrant(m.admin(), Role.MANAGER, "SCHOOL_CONTRACTS", "EDIT", false);
        }
    }

    @Test
    void teacherAndSystemAreRefusedEverywhere() {
        Mou m = mou();
        for (Role role : new Role[] {Role.TEACHER, Role.SYSTEM}) {
            String token = signInAs(role).token();
            assertThat(approval(token, m.contractId()).status()).as(role.name()).isEqualTo(403);
            assertThat(upload(token, m.contractId(), "a.pdf", PDF).status()).as(role.name()).isEqualTo(403);
            assertThat(download(token, m.contractId(), UUID.randomUUID()).status()).as(role.name()).isEqualTo(403);
        }
    }

    @Test
    void thePlaceholderContractRefusesAnUploadUntilTheTermsAreRecorded() {
        String admin = signInAs(Role.ADMIN).token();
        Fixture f = fixture(admin);
        UUID teacher = teacher(admin);
        placeTeacher(admin, teacher, f.schoolId());
        UUID pending = UUID.fromString((String) contractsOf(admin, f.schoolId()).get(0).get("id"));

        Resp resp = upload(admin, pending, "a.pdf", PDF);

        assertThat(resp.status()).isEqualTo(409);
        assertThat(resp.body()).contains("Record the MoU terms first");
        assertThat(approval(admin, pending).map()).containsEntry("state", "NOT_APPLICABLE");
    }

    @Test
    void aCancelledContractKeepsItsDocumentDownloadableButRefusesSubmit() {
        Mou m = mou();
        Resp uploaded = upload(m.admin(), m.contractId(), "a.pdf", PDF);
        UUID doc = firstDocumentId(uploaded);
        assertThat(post(BASE + "/contracts/" + m.contractId() + "/cancel", m.admin(), null).status()).isEqualTo(200);

        assertThat(download(m.admin(), m.contractId(), doc).status()).isEqualTo(200);
        assertThat(post(
                                BASE + "/contracts/" + m.contractId() + "/submit",
                                m.admin(),
                                Map.of("version", versionOf(m.admin(), m.contractId())))
                        .status())
                .isEqualTo(409);
    }

    @Test
    void downloadIsAnAttachmentWithNosniffAndTheServerDecidedType() {
        Mou m = mou();
        Resp uploaded = upload(m.admin(), m.contractId(), "board.png", PNG);
        UUID doc = firstDocumentId(uploaded);

        var response = client.method(HttpMethod.GET)
                .uri(BASE + "/contracts/" + m.contractId() + "/documents/" + doc + "/file")
                .header("Authorization", "Bearer " + m.managerToken())
                .exchange()
                .returnResult(byte[].class);

        assertThat(response.getStatus().value()).isEqualTo(200);
        assertThat(response.getResponseHeaders().getFirst("Content-Disposition")).startsWith("attachment");
        assertThat(response.getResponseHeaders().getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(response.getResponseHeaders().getFirst("Content-Type")).isEqualTo("image/png");
        assertThat(response.getResponseBody()).isEqualTo(PNG);
    }

    @Test
    void removingNeedsAReasonKeepsTheStatusAndHidesTheVersion() {
        Mou m = mou();
        UUID doc = firstDocumentId(upload(m.admin(), m.contractId(), "a.pdf", PDF));

        assertThat(removeDocument(m.admin(), m.contractId(), doc, null).status()).isEqualTo(400);
        assertThat(removeDocument(m.admin(), m.contractId(), doc, "  ").status()).isEqualTo(400);
        assertThat(removeDocument(m.admin(), m.contractId(), doc, "x".repeat(301)).status()).isEqualTo(400);

        Resp removed = removeDocument(m.admin(), m.contractId(), doc, "wrong file");
        assertThat(removed.status()).as(removed.body()).isEqualTo(200);
        assertThat(removed.map()).containsEntry("state", "DRAFT");
        assertThat(documentsOf(removed).get(0).get("removed").toString()).contains("wrong file");
        assertThat(documentsOf(removed).get(0)).containsEntry("current", false);
        assertThat(((Map<?, ?>) removed.map().get("document")).get("state")).isEqualTo("NONE");
        assertThat(download(m.admin(), m.contractId(), doc).status()).isEqualTo(404);
        assertThat(removeDocument(m.admin(), m.contractId(), doc, "again").status()).isEqualTo(404);
        assertThat(historyActions(removed)).contains("DOCUMENT_REMOVED");
    }

    @Test
    void aRemovedLatestVersionFallsBackToThePreviousOne() {
        Mou m = mou();
        upload(m.admin(), m.contractId(), "v1.pdf", PDF);
        Resp second = upload(m.admin(), m.contractId(), "v2.pdf", PDF);
        UUID v2 = firstDocumentId(second);

        Resp removed = removeDocument(m.admin(), m.contractId(), v2, "scan was blurred");

        var docs = documentsOf(removed);
        assertThat(docs.get(0)).containsEntry("versionNo", 2).containsEntry("current", false);
        assertThat(docs.get(1)).containsEntry("versionNo", 1).containsEntry("current", true);
        assertThat(((Map<?, ?>) removed.map().get("document")).get("current").toString()).contains("v1.pdf");
    }

    @Test
    void versionNumbersKeepCountingAfterARemoval() {
        Mou m = mou();
        UUID v1 = firstDocumentId(upload(m.admin(), m.contractId(), "v1.pdf", PDF));
        removeDocument(m.admin(), m.contractId(), v1, "wrong");

        Resp next = upload(m.admin(), m.contractId(), "v2.pdf", PDF);

        assertThat(documentsOf(next).get(0)).containsEntry("versionNo", 2).containsEntry("current", true);
        assertThat(historyActions(next)).containsExactly("DOCUMENT_ADDED", "DOCUMENT_REMOVED", "DOCUMENT_ADDED");
    }
}
