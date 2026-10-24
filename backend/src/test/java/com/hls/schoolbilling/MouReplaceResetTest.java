package com.hls.schoolbilling;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Spec 031 FR-009 and clarifications: what a new or removed document does to each status. */
class MouReplaceResetTest extends MouTestBase {

    private UUID submitterOf(Resp approval) {
        return UUID.fromString((String) ((java.util.Map<?, ?>) approval.map().get("submittedBy")).get("id"));
    }

    @Test
    void aNewVersionOnAnApprovedMoUResetsItToPendingWithTheUploaderAsSubmitter() {
        Mou m = mou();
        String dir = tokenOf(m.director());
        upload(m.admin(), m.contractId(), "v1.pdf", PDF);
        submit(m.admin(), m.contractId());
        approve(dir, m.contractId());
        assertThat(stateOf(m.admin(), m.contractId())).isEqualTo("APPROVED");
        int before = notifications(m.director().userId(), "MOU_SUBMITTED");

        Resp reset = upload(m.admin(), m.contractId(), "v2.pdf", PDF);

        assertThat(reset.status()).as(reset.body()).isEqualTo(201);
        assertThat(reset.map()).containsEntry("state", "PENDING").containsEntry("round", 2);
        assertThat(historyActions(reset)).containsExactly("DOCUMENT_ADDED", "SUBMITTED", "APPROVED", "DOCUMENT_REPLACED", "RESET_TO_PENDING");
        assertThat(((java.util.Map<?, ?>) reset.map().get("document")).get("state")).isEqualTo("ATTACHED");
        assertThat(notifications(m.director().userId(), "MOU_SUBMITTED")).isEqualTo(before + 1);
        assertThat(reset.map().get("decidedBy")).isNull();
        assertThat(submitterOf(reset)).isNotNull();
    }

    @Test
    void onDraftPendingAndRejectedANewVersionOnlyAddsAHistoryEntry() {
        Mou m = mou();
        upload(m.admin(), m.contractId(), "v1.pdf", PDF);
        assertThat(upload(m.admin(), m.contractId(), "v2.pdf", PDF).map()).containsEntry("state", "DRAFT").containsEntry("round", 0);

        submit(m.admin(), m.contractId());
        Resp pending = upload(m.admin(), m.contractId(), "v3.pdf", PDF);
        assertThat(pending.map()).containsEntry("state", "PENDING").containsEntry("round", 1);

        reject(m.managerToken(), m.contractId(), "wrong scan");
        Resp rejected = upload(m.admin(), m.contractId(), "v4.pdf", PDF);
        assertThat(rejected.map()).containsEntry("state", "REJECTED").containsEntry("round", 1);
        assertThat(historyActions(rejected)).doesNotContain("RESET_TO_PENDING");
    }

    @Test
    void aLegacyMoUStaysLegacyWhenADocumentIsAttachedOrReplaced() {
        Mou m = mou();
        makeLegacy(m.contractId());

        Resp first = upload(m.admin(), m.contractId(), "v1.pdf", PDF);
        Resp second = upload(m.admin(), m.contractId(), "v2.pdf", PDF);

        assertThat(first.map()).containsEntry("state", "APPROVED_BEFORE_APPROVAL_STEP");
        assertThat(second.map()).containsEntry("state", "APPROVED_BEFORE_APPROVAL_STEP").containsEntry("round", 0);
        assertThat(historyActions(second)).doesNotContain("RESET_TO_PENDING");
        assertThat(actionsOf(second)).doesNotContain("SUBMIT", "APPROVE", "REJECT");
        assertThat(submit(m.admin(), m.contractId()).status()).isEqualTo(409);
    }

    @Test
    void removingTheCurrentDocumentOfAnApprovedMoUKeepsItApprovedWithoutDocument() {
        Mou m = mou();
        UUID doc = firstDocumentId(upload(m.admin(), m.contractId(), "v1.pdf", PDF));
        submit(m.admin(), m.contractId());
        approve(tokenOf(m.director()), m.contractId());

        Resp removed = removeDocument(m.admin(), m.contractId(), doc, "wrong file");

        assertThat(removed.status()).as(removed.body()).isEqualTo(200);
        assertThat(removed.map()).containsEntry("state", "APPROVED_WITHOUT_DOCUMENT").containsEntry("round", 1);
    }
}
