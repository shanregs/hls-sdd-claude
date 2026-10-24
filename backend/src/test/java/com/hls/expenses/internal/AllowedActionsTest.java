package com.hls.expenses.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.permissions.PermissionAction;
import com.hls.identity.permissions.PermissionModule;
import java.util.List;
import java.util.Set;
import java.util.function.BiPredicate;
import org.junit.jupiter.api.Test;

/** Spec 015 T014: the allowed actions of a claim for each kind of viewer and status (FR-015, FR-031). */
class AllowedActionsTest {

    private static final BiPredicate<PermissionModule, PermissionAction> CLAIMANT = grants(
            PermissionModule.MY_EXPENSES, Set.of(PermissionAction.VIEW, PermissionAction.CREATE, PermissionAction.EDIT, PermissionAction.DELETE));

    private static final BiPredicate<PermissionModule, PermissionAction> ADMIN = (m, a) ->
            m == PermissionModule.EXPENSES || CLAIMANT.test(m, a);

    private static final BiPredicate<PermissionModule, PermissionAction> DIRECTOR = (m, a) ->
            (m == PermissionModule.EXPENSES && a != PermissionAction.PROCESS) || CLAIMANT.test(m, a);

    private static BiPredicate<PermissionModule, PermissionAction> grants(PermissionModule module, Set<PermissionAction> actions) {
        return (m, a) -> m == module && actions.contains(a);
    }

    private static List<String> actions(boolean own, String status, String paidBy, boolean locked, BiPredicate<PermissionModule, PermissionAction> granted) {
        return AllowedActions.of(own, ClaimStatus.valueOf(status), PaidBy.valueOf(paidBy), locked, granted);
    }

    @Test
    void claimantOnADraftMayEditSubmitAddBillsAndDelete() {
        assertThat(actions(true, "DRAFT", "CLAIMANT", false, CLAIMANT))
                .containsExactlyInAnyOrder("EDIT", "SUBMIT", "ADD_BILL", "DELETE");
    }

    @Test
    void claimantOnARejectedClaimMayCorrectAndResubmitButNotDelete() {
        assertThat(actions(true, "REJECTED", "CLAIMANT", false, CLAIMANT)).containsExactlyInAnyOrder("EDIT", "SUBMIT", "ADD_BILL");
    }

    @Test
    void claimantOnASubmittedClaimMayOnlyWithdraw() {
        assertThat(actions(true, "SUBMITTED", "CLAIMANT", false, CLAIMANT)).containsExactly("WITHDRAW");
    }

    @Test
    void claimantOnApprovedAndPaidClaimsHasNoActions() {
        assertThat(actions(true, "APPROVED", "CLAIMANT", false, CLAIMANT)).isEmpty();
        assertThat(actions(true, "PAID", "CLAIMANT", false, CLAIMANT)).isEmpty();
        assertThat(actions(true, "PAID_BY_COMPANY", "COMPANY", false, CLAIMANT)).isEmpty();
    }

    @Test
    void adminOnSomeoneElsesSubmittedClaimMayDecideAndRemoveABill() {
        assertThat(actions(false, "SUBMITTED", "CLAIMANT", false, ADMIN)).containsExactlyInAnyOrder("APPROVE", "REJECT", "REMOVE_BILL");
    }

    @Test
    void adminOnAnApprovedPersonalClaimMayReverseAndRecordPaidUnlessPayrollHoldsIt() {
        assertThat(actions(false, "APPROVED", "CLAIMANT", false, ADMIN)).containsExactlyInAnyOrder("REVERSE", "RECORD_PAID", "REMOVE_BILL");
        assertThat(actions(false, "APPROVED", "CLAIMANT", true, ADMIN)).containsExactly("REMOVE_BILL");
    }

    @Test
    void directorMayReverseButNeverRecordPayment() {
        assertThat(actions(false, "APPROVED", "CLAIMANT", false, DIRECTOR)).containsExactlyInAnyOrder("REVERSE", "REMOVE_BILL");
    }

    @Test
    void aPaidClaimOffersNothingToReverseOrRecord() {
        assertThat(actions(false, "PAID", "CLAIMANT", false, ADMIN)).containsExactly("REMOVE_BILL");
        assertThat(actions(false, "PAID_BY_COMPANY", "COMPANY", false, ADMIN)).containsExactly("REMOVE_BILL");
    }

    @Test
    void aCompanyPaidApprovedClaimCannotBeRecordedPaid() {
        assertThat(actions(false, "APPROVED", "COMPANY", false, ADMIN)).doesNotContain("RECORD_PAID");
    }

    @Test
    void aUserHoldingBothRolesNeverDecidesOrReversesTheirOwnClaim() {
        assertThat(actions(true, "SUBMITTED", "CLAIMANT", false, ADMIN)).containsExactly("WITHDRAW");
        assertThat(actions(true, "SUBMITTED", "CLAIMANT", false, ADMIN)).doesNotContain("APPROVE", "REJECT", "REVERSE");
        assertThat(actions(true, "APPROVED", "CLAIMANT", false, ADMIN)).containsExactly("RECORD_PAID");
        assertThat(actions(true, "APPROVED", "CLAIMANT", false, DIRECTOR)).isEmpty();
    }

    @Test
    void aViewerWithNoGrantsGetsNothing() {
        assertThat(actions(false, "SUBMITTED", "CLAIMANT", false, (m, a) -> false)).isEmpty();
        assertThat(actions(true, "DRAFT", "CLAIMANT", false, (m, a) -> false)).isEmpty();
    }
}
