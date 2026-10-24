package com.hls.schoolbilling;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Spec 031 SC-003 (documents): a Zone Manager of another Zone never learns that a MoU or its document exists. */
class MouDocumentScopeTest extends MouTestBase {

    @Test
    void aManagerOfAnotherZoneGets404ForEveryDocumentEndpoint() {
        Mou m = mou();
        UUID doc = firstDocumentId(upload(m.admin(), m.contractId(), "a.pdf", PDF));
        Fixture other = fixture(m.admin());
        String outsider = other.manager().token();

        assertThat(approval(outsider, m.contractId()).status()).isEqualTo(404);
        assertThat(download(outsider, m.contractId(), doc).status()).isEqualTo(404);
        // without Edit the grant answers first (the same 403 for every contract, so nothing is revealed)
        assertThat(upload(outsider, m.contractId(), "b.pdf", PDF).status()).isEqualTo(403);
        setGrant(m.admin(), com.hls.identity.user.Role.MANAGER, "SCHOOL_CONTRACTS", "EDIT", true);
        try {
            assertThat(upload(outsider, m.contractId(), "b.pdf", PDF).status()).isEqualTo(404);
            assertThat(removeDocument(outsider, m.contractId(), doc, "x").status()).isEqualTo(404);
        } finally {
            setGrant(m.admin(), com.hls.identity.user.Role.MANAGER, "SCHOOL_CONTRACTS", "EDIT", false);
        }
        // the Manager of the School still sees it
        assertThat(download(m.managerToken(), m.contractId(), doc).status()).isEqualTo(200);
        assertThat(approval(m.managerToken(), m.contractId()).status()).isEqualTo(200);
    }

    @Test
    void aDocumentIdOfAnotherContractInTheUrlAnswers404() {
        Mou a = mou();
        Mou b = mou();
        UUID docOfB = firstDocumentId(upload(b.admin(), b.contractId(), "b.pdf", PDF));

        assertThat(download(a.admin(), a.contractId(), docOfB).status()).isEqualTo(404);
        assertThat(removeDocument(a.admin(), a.contractId(), docOfB, "x").status()).isEqualTo(404);
    }

    @Test
    void theStoredFileIdIsNeitherExposedNorARoute() {
        Mou m = mou();
        upload(m.admin(), m.contractId(), "a.pdf", PDF);

        assertThat(approval(m.admin(), m.contractId()).body()).doesNotContain("fileId");
        assertThat(get(BASE + "/files/" + UUID.randomUUID(), m.admin()).status()).isIn(404, 405);
    }
}
