package com.hls.schoolbilling;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Spec 031 US2 and SC-002: every role against submit, approve, reject and amend. */
class MouApprovalApiTest extends MouTestBase {

    @Test
    void submitWorksWithoutADocumentAndWithOne() {
        Mou a = mou();
        Resp withoutDocument = submit(a.admin(), a.contractId());
        assertThat(withoutDocument.status()).as(withoutDocument.body()).isEqualTo(200);
        assertThat(withoutDocument.map()).containsEntry("state", "PENDING").containsEntry("round", 1);
        assertThat(historyActions(withoutDocument)).containsExactly("SUBMITTED");

        Mou b = mou();
        upload(b.admin(), b.contractId(), "a.pdf", PDF);
        Resp withDocument = submit(b.admin(), b.contractId());
        assertThat(withDocument.map()).containsEntry("state", "PENDING");
        assertThat(actionsOf(withDocument)).containsExactly("UPLOAD", "REMOVE_DOCUMENT");
    }

    @Test
    void adminSubmitsButNeverApprovesRejectsOrAmends() {
        Mou m = mou();
        submit(m.admin(), m.contractId());
        long v = versionOf(m.admin(), m.contractId());

        assertThat(post(BASE + "/contracts/" + m.contractId() + "/approve", m.admin(), Map.of("version", v)).status())
                .isEqualTo(403);
        assertThat(post(BASE + "/contracts/" + m.contractId() + "/reject", m.admin(), Map.of("version", v, "reason", "x")).status())
                .isEqualTo(403);
        assertThat(patchTerms(m.admin(), m.contractId(), Map.of("version", v, "rate", "16000")).status()).isEqualTo(403);
        assertThat(get(BASE + "/approvals/pending", m.admin()).status()).isEqualTo(403);
        assertThat(actionsOf(approval(m.admin(), m.contractId()))).doesNotContain("APPROVE", "REJECT", "AMEND_TERMS");
    }

    @Test
    void teacherAndSystemAreRefusedOnEveryAction() {
        Mou m = mou();
        submit(m.admin(), m.contractId());
        for (Role role : new Role[] {Role.TEACHER, Role.SYSTEM}) {
            String token = signInAs(role).token();
            for (String path : new String[] {"/submit", "/approve", "/reject", "/terms"}) {
                Resp resp = path.equals("/terms")
                        ? patchTerms(token, m.contractId(), Map.of("version", 0))
                        : post(BASE + "/contracts/" + m.contractId() + path, token, Map.of("version", 0, "reason", "x"));
                assertThat(resp.status()).as(role + " " + path).isEqualTo(403);
            }
            assertThat(get(BASE + "/approvals/pending", token).status()).as(role.name()).isEqualTo(403);
        }
    }

    @Test
    void theZoneManagerOfTheSchoolApprovesAndAnotherZonesManagerGets404() {
        Mou m = mou();
        submit(m.admin(), m.contractId());
        long v = versionOf(m.admin(), m.contractId());
        String outsider = fixture(m.admin()).manager().token();

        assertThat(post(BASE + "/contracts/" + m.contractId() + "/approve", outsider, Map.of("version", v)).status())
                .isEqualTo(404);
        assertThat(post(BASE + "/contracts/" + m.contractId() + "/reject", outsider, Map.of("version", v, "reason", "x")).status())
                .isEqualTo(404);
        assertThat(patchTerms(outsider, m.contractId(), Map.of("version", v, "rate", "16000")).status()).isEqualTo(404);
        assertThat(stateOf(m.admin(), m.contractId())).isEqualTo("PENDING");

        Resp approved = post(BASE + "/contracts/" + m.contractId() + "/approve", m.managerToken(), Map.of("version", v));
        assertThat(approved.status()).as(approved.body()).isEqualTo(200);
        assertThat(approved.map()).containsEntry("state", "APPROVED_WITHOUT_DOCUMENT").containsEntry("selfDecided", false);
        assertThat(historyOf(approved).get(1)).containsEntry("action", "APPROVED").containsEntry("actorRole", "MANAGER");
    }

    @Test
    void aDirectorApprovesAnySchool() {
        Mou m = mou();
        upload(m.admin(), m.contractId(), "a.pdf", PDF);
        submit(m.admin(), m.contractId());

        Resp approved = approve(tokenOf(m.director()), m.contractId());

        assertThat(approved.status()).as(approved.body()).isEqualTo(200);
        assertThat(approved.map()).containsEntry("state", "APPROVED");
        assertThat(approved.map().get("decidedBy").toString()).contains(m.director().userId().toString());
    }

    @Test
    void aManagerMayNotSubmitEvenInScope() {
        Mou m = mou();
        assertThat(post(BASE + "/contracts/" + m.contractId() + "/submit", m.managerToken(), Map.of("version", 0)).status())
                .isEqualTo(403);
    }

    @Test
    void rejectNeedsAReasonOfAtMost500CharactersAndResubmitStartsRoundTwo() {
        Mou m = mou();
        submit(m.admin(), m.contractId());
        String manager = m.managerToken();

        assertThat(reject(manager, m.contractId(), "  ").status()).isEqualTo(400);
        assertThat(reject(manager, m.contractId(), "x".repeat(501)).status()).isEqualTo(400);
        Resp rejected = reject(manager, m.contractId(), "Rate does not match the signed copy");
        assertThat(rejected.status()).as(rejected.body()).isEqualTo(200);
        assertThat(rejected.map())
                .containsEntry("state", "REJECTED")
                .containsEntry("decisionReason", "Rate does not match the signed copy");

        Resp again = submit(m.admin(), m.contractId());
        assertThat(again.map()).containsEntry("state", "PENDING").containsEntry("round", 2);
        assertThat(historyActions(again)).containsExactly("SUBMITTED", "REJECTED", "RESUBMITTED");
        assertThat(historyOf(again).get(1)).containsEntry("note", "Rate does not match the signed copy").containsEntry("round", 1);
    }

    @Test
    void aRejectWithoutAReasonFromAnOutsiderStillAnswers404() {
        Mou m = mou();
        submit(m.admin(), m.contractId());
        String outsider = fixture(m.admin()).manager().token();

        assertThat(post(BASE + "/contracts/" + m.contractId() + "/reject", outsider, Map.of("version", 0, "reason", "")).status())
                .isEqualTo(404);
    }

    @Test
    void aDirectorMayApproveWhatTheySubmittedAndItIsAuditedAsSuch() {
        Mou m = mou();
        String dir = tokenOf(m.director());
        submit(dir, m.contractId());

        Resp approved = approve(dir, m.contractId());

        assertThat(approved.status()).as(approved.body()).isEqualTo(200);
        assertThat(approved.map()).containsEntry("selfDecided", true);
        var history = historyOf(approved);
        assertThat(history.get(0).get("actor")).isEqualTo(history.get(1).get("actor"));
        assertChangeRecorded(m.admin(), "MOU_APPROVAL", m.contractId(), "selfDecided");
    }

    @Test
    void decidingAnythingNotPendingAnswers409() {
        Mou m = mou();
        String dir = tokenOf(m.director());
        // DRAFT
        assertThat(approve(dir, m.contractId()).status()).isEqualTo(409);
        submit(m.admin(), m.contractId());
        approve(dir, m.contractId());
        // APPROVED
        Resp again = approve(dir, m.contractId());
        assertThat(again.status()).isEqualTo(409);
        assertThat(again.body()).contains("already decided").contains("approved by");
        assertThat(reject(dir, m.contractId(), "late").status()).isEqualTo(409);
        // submitting an approved MoU
        assertThat(submit(m.admin(), m.contractId()).status()).isEqualTo(409);
    }

    @Test
    void aStaleVersionAnswers409() {
        Mou m = mou();
        submit(m.admin(), m.contractId());
        long v = versionOf(m.admin(), m.contractId());

        Resp stale = post(BASE + "/contracts/" + m.contractId() + "/approve", tokenOf(m.director()), Map.of("version", v - 1));

        assertThat(stale.status()).isEqualTo(409);
        assertThat(stateOf(m.admin(), m.contractId())).isEqualTo("PENDING");
    }

    @Test
    void aCancelledOrPlaceholderContractCannotBeSubmittedOrDecided() {
        Mou m = mou();
        submit(m.admin(), m.contractId());
        assertThat(post(BASE + "/contracts/" + m.contractId() + "/cancel", m.admin(), null).status()).isEqualTo(200);

        assertThat(approve(tokenOf(m.director()), m.contractId()).status()).isEqualTo(409);
        assertThat(submit(m.admin(), m.contractId()).status()).isEqualTo(409);

        String admin = m.admin();
        Fixture f = fixture(admin);
        placeTeacher(admin, teacher(admin), f.schoolId());
        UUID placeholder = UUID.fromString((String) contractsOf(admin, f.schoolId()).get(0).get("id"));
        assertThat(post(BASE + "/contracts/" + placeholder + "/submit", admin, Map.of("version", 0)).status()).isEqualTo(409);
        assertThat(post(BASE + "/contracts/" + placeholder + "/approve", tokenOf(m.director()), Map.of("version", 0)).status())
                .isEqualTo(409);
    }
}
