package com.hls.schoolbilling;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Spec 031 FR-017: upload, replace and removal are in the approval history and in Change History. */
class MouDocumentAuditTest extends MouTestBase {

    @Test
    void uploadReplaceAndRemovalAreRecordedWithActorRoleAndReason() {
        Mou m = mou();
        Resp first = upload(m.admin(), m.contractId(), "v1.pdf", PDF);
        Resp second = upload(m.admin(), m.contractId(), "v2.pdf", PDF);
        UUID v2 = firstDocumentId(second);
        Resp removed = removeDocument(m.admin(), m.contractId(), v2, "blurred scan");

        assertThat(first.status()).isEqualTo(201);
        var history = historyOf(removed);
        assertThat(historyActions(removed)).containsExactly("DOCUMENT_ADDED", "DOCUMENT_REPLACED", "DOCUMENT_REMOVED");
        assertThat(history.get(0)).containsEntry("actorRole", "ADMIN").containsEntry("documentVersion", 1);
        assertThat(history.get(2)).containsEntry("note", "blurred scan").containsEntry("documentVersion", 2);
        assertThat(history.get(2).get("actor")).isNotNull();
        assertChangeRecorded(m.admin(), "MOU_DOCUMENT", m.contractId(), "added");
        assertChangeRecorded(m.admin(), "MOU_DOCUMENT", m.contractId(), "replaced");
        assertChangeRecorded(m.admin(), "MOU_DOCUMENT", m.contractId(), "removed");
    }

    @Test
    void aDirectorUploadIsRecordedWithTheDirectorRole() {
        Mou m = mou();
        String dir = signIn(m.director().userId(), m.director().phone(), PASSWORD).token();

        Resp resp = upload(dir, m.contractId(), "v1.pdf", PDF);

        assertThat(historyOf(resp).get(0)).containsEntry("actorRole", "DIRECTOR");
    }
}
