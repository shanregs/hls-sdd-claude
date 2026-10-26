package com.hls.schoolbilling;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import com.hls.schoolbilling.api.ContractView;
import com.hls.schoolbilling.api.MouApprovalState;
import com.hls.schoolbilling.api.MouApprovals;
import com.hls.schoolbilling.api.SchoolContracts;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Spec 031 US4 and SC-007: the approval state of the MoU in effect, for billing, incentives and marketing. */
class MouApprovalsPublicApiTest extends MouTestBase {

    @Autowired
    private MouApprovals approvals;

    @Autowired
    private SchoolContracts contracts;

    @Test
    void everyStateIsReportedForTheMoUInEffect() {
        Mou draft = mou();
        Mou pending = mou();
        submit(pending.admin(), pending.contractId());
        Mou rejected = mou();
        submit(rejected.admin(), rejected.contractId());
        reject(rejected.managerToken(), rejected.contractId(), "no");
        Mou approvedWithDoc = mou();
        upload(approvedWithDoc.admin(), approvedWithDoc.contractId(), "a.pdf", PDF);
        submit(approvedWithDoc.admin(), approvedWithDoc.contractId());
        approve(tokenOf(approvedWithDoc.director()), approvedWithDoc.contractId());
        Mou approvedNoDoc = mou();
        submit(approvedNoDoc.admin(), approvedNoDoc.contractId());
        approve(approvedNoDoc.managerToken(), approvedNoDoc.contractId());
        Mou legacy = mou();
        makeLegacy(legacy.contractId());

        assertThat(approvals.stateOn(draft.f().schoolId(), today))
                .extracting(MouApprovalState::state, MouApprovalState::countsAsApproved, MouApprovalState::documentAttached)
                .containsExactly("DRAFT", false, false);
        assertThat(approvals.stateOn(pending.f().schoolId(), today).state()).isEqualTo("PENDING");
        assertThat(approvals.stateOn(rejected.f().schoolId(), today).state()).isEqualTo("REJECTED");
        MouApprovalState withDoc = approvals.stateOn(approvedWithDoc.f().schoolId(), today);
        assertThat(withDoc.state()).isEqualTo("APPROVED");
        assertThat(withDoc.countsAsApproved()).isTrue();
        assertThat(withDoc.documentAttached()).isTrue();
        assertThat(withDoc.approvedOn()).isNotNull();
        assertThat(withDoc.contractId()).isEqualTo(approvedWithDoc.contractId());
        MouApprovalState noDoc = approvals.stateOn(approvedNoDoc.f().schoolId(), today);
        assertThat(noDoc.state()).isEqualTo("APPROVED_WITHOUT_DOCUMENT");
        assertThat(noDoc.countsAsApproved()).isTrue();
        assertThat(noDoc.approvedOn()).isNotNull();
        MouApprovalState old = approvals.stateOn(legacy.f().schoolId(), today);
        assertThat(old.state()).isEqualTo("APPROVED_BEFORE_APPROVAL_STEP");
        assertThat(old.countsAsApproved()).isTrue();
        assertThat(old.approvedOn()).isNull();
    }

    @Test
    void aSchoolWithNoMoUOnlyAPlaceholderOrACancelledOneAreNotApproved() {
        String admin = signInAs(Role.ADMIN).token();
        Fixture none = fixture(admin);
        Fixture placeholder = fixture(admin);
        placeTeacher(admin, teacher(admin), placeholder.schoolId());
        Mou cancelled = mou();
        post(BASE + "/contracts/" + cancelled.contractId() + "/cancel", cancelled.admin(), null);

        MouApprovalState noMou = approvals.stateOn(none.schoolId(), today);
        assertThat(noMou.state()).isEqualTo("NO_MOU");
        assertThat(noMou.contractId()).isNull();
        assertThat(noMou.countsAsApproved()).isFalse();
        assertThat(approvals.stateOn(placeholder.schoolId(), today).state()).isEqualTo("NOT_APPLICABLE");
        assertThat(approvals.stateOn(cancelled.f().schoolId(), today).state()).isEqualTo("NO_MOU");
        assertThat(approvals.stateOn(placeholder.schoolId(), today).countsAsApproved()).isFalse();
    }

    @Test
    void anEndedMoUIsNotInEffectAndTheChosenOneMatchesSchoolContracts() {
        Mou m = mou();
        submit(m.admin(), m.contractId());
        approve(m.managerToken(), m.contractId());
        post(BASE + "/contracts/" + m.contractId() + "/end", m.admin(), Map.of("endsOn", today.toString()));

        assertThat(approvals.stateOn(m.f().schoolId(), today).contractId()).isEqualTo(m.contractId());
        assertThat(approvals.stateOn(m.f().schoolId(), today.plusDays(1)).state()).isEqualTo("NO_MOU");
        ContractView view = contracts.contractOf(m.f().schoolId(), today).orElseThrow();
        assertThat(approvals.stateOn(m.f().schoolId(), today).contractId()).isEqualTo(view.id());
    }

    @Test
    void stateOfAllHasAnEntryForEveryIdInOneCall() {
        Mou a = mou();
        Mou b = mou();
        UUID unknown = UUID.randomUUID();

        Map<UUID, MouApprovalState> states = approvals.stateOfAll(List.of(a.f().schoolId(), b.f().schoolId(), unknown), today);

        assertThat(states).containsOnlyKeys(a.f().schoolId(), b.f().schoolId(), unknown);
        assertThat(states.get(unknown).state()).isEqualTo("NO_MOU");
        assertThat(states.get(a.f().schoolId()).state()).isEqualTo("DRAFT");
        assertThat(approvals.stateOfAll(List.of(), today)).isEmpty();
    }
}
