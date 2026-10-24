package com.hls.expenses.internal;

import com.hls.identity.permissions.PermissionAction;
import com.hls.identity.permissions.PermissionModule;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;

/**
 * The actions a viewer may take on a claim, derived from their grants, whether they raised the claim and its status
 * (spec 015 FR-031, contracts/expenses-api.md). The server enforces the same rules again on every action; this list only
 * tells a client which buttons to show. Nobody approves, rejects or reverses a claim they raised, even holding both
 * approver roles (FR-015).
 */
final class AllowedActions {

    static final String EDIT = "EDIT";
    static final String SUBMIT = "SUBMIT";
    static final String WITHDRAW = "WITHDRAW";
    static final String DELETE = "DELETE";
    static final String ADD_BILL = "ADD_BILL";
    static final String APPROVE = "APPROVE";
    static final String REJECT = "REJECT";
    static final String REVERSE = "REVERSE";
    static final String RECORD_PAID = "RECORD_PAID";
    static final String REMOVE_BILL = "REMOVE_BILL";

    private AllowedActions() {}

    /**
     * @param own the viewer raised the claim
     * @param payrollLocked the claim's pay run is awaiting approval or has applied it, so it cannot be reversed or recorded paid
     * @param granted whether the viewer holds a grant (module and action)
     */
    static List<String> of(
            boolean own,
            ClaimStatus status,
            PaidBy paidBy,
            boolean payrollLocked,
            BiPredicate<PermissionModule, PermissionAction> granted) {
        List<String> actions = new ArrayList<>();
        boolean myEdit = own && granted.test(PermissionModule.MY_EXPENSES, PermissionAction.EDIT);
        boolean review = granted.test(PermissionModule.EXPENSES, PermissionAction.APPROVE);
        boolean process = granted.test(PermissionModule.EXPENSES, PermissionAction.PROCESS);
        if (myEdit && status.editable()) {
            actions.add(EDIT);
            actions.add(SUBMIT);
            actions.add(ADD_BILL);
        }
        if (myEdit && status == ClaimStatus.SUBMITTED) {
            actions.add(WITHDRAW);
        }
        if (own && status == ClaimStatus.DRAFT && granted.test(PermissionModule.MY_EXPENSES, PermissionAction.DELETE)) {
            actions.add(DELETE);
        }
        if (!own && review && status == ClaimStatus.SUBMITTED) {
            actions.add(APPROVE);
            actions.add(REJECT);
        }
        if (!own && (review || process) && status == ClaimStatus.APPROVED && !payrollLocked) {
            actions.add(REVERSE);
        }
        if (process && status == ClaimStatus.APPROVED && paidBy == PaidBy.CLAIMANT && !payrollLocked) {
            actions.add(RECORD_PAID);
        }
        if (!own && review && status != ClaimStatus.DRAFT) {
            actions.add(REMOVE_BILL);
        }
        return actions;
    }
}
