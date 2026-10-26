package com.hls.schoolbilling;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.schoolbilling.internal.ContractState;
import com.hls.schoolbilling.internal.MouApprovalStatus;
import com.hls.schoolbilling.internal.MouShownState;
import org.junit.jupiter.api.Test;

/** Spec 031 research.md section 5: the shown state derived from the contract, the stored status and the document. */
class MouApprovalStatusTest {

    private static MouShownState shown(ContractState c, MouApprovalStatus s, boolean doc) {
        return MouShownState.derive(c, s, doc);
    }

    @Test
    void placeholderAndCancelledContractsHaveTheirOwnState() {
        assertThat(shown(ContractState.RATE_PENDING, null, false)).isEqualTo(MouShownState.NOT_APPLICABLE);
        assertThat(shown(ContractState.CANCELLED, MouApprovalStatus.APPROVED, true)).isEqualTo(MouShownState.CANCELLED);
        assertThat(shown(ContractState.CANCELLED, null, false)).isEqualTo(MouShownState.CANCELLED);
    }

    @Test
    void legacyIsShownAsApprovedBeforeTheApprovalStepWhateverTheDocument() {
        assertThat(shown(ContractState.ACTIVE, MouApprovalStatus.APPROVED_LEGACY, false))
                .isEqualTo(MouShownState.APPROVED_BEFORE_APPROVAL_STEP);
        assertThat(shown(ContractState.ACTIVE, MouApprovalStatus.APPROVED_LEGACY, true))
                .isEqualTo(MouShownState.APPROVED_BEFORE_APPROVAL_STEP);
    }

    @Test
    void approvedIsWithOrWithoutDocument() {
        assertThat(shown(ContractState.ACTIVE, MouApprovalStatus.APPROVED, true)).isEqualTo(MouShownState.APPROVED);
        assertThat(shown(ContractState.ACTIVE, MouApprovalStatus.APPROVED, false))
                .isEqualTo(MouShownState.APPROVED_WITHOUT_DOCUMENT);
    }

    @Test
    void draftPendingAndRejectedKeepTheirNameWithOrWithoutDocument() {
        for (boolean doc : new boolean[] {true, false}) {
            assertThat(shown(ContractState.ACTIVE, MouApprovalStatus.DRAFT, doc)).isEqualTo(MouShownState.DRAFT);
            assertThat(shown(ContractState.ACTIVE, MouApprovalStatus.PENDING, doc)).isEqualTo(MouShownState.PENDING);
            assertThat(shown(ContractState.ACTIVE, MouApprovalStatus.REJECTED, doc)).isEqualTo(MouShownState.REJECTED);
        }
    }

    @Test
    void anActiveContractWithoutAnApprovalRowIsNeverShownAsApproved() {
        assertThat(shown(ContractState.ACTIVE, null, true).countsAsApproved()).isFalse();
    }

    @Test
    void onlyTheThreeApprovedViewsCountAsApproved() {
        for (MouShownState s : MouShownState.values()) {
            boolean expected = s == MouShownState.APPROVED
                    || s == MouShownState.APPROVED_WITHOUT_DOCUMENT
                    || s == MouShownState.APPROVED_BEFORE_APPROVAL_STEP;
            assertThat(s.countsAsApproved()).as(s.name()).isEqualTo(expected);
        }
    }
}
