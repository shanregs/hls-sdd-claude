package com.hls.organization.api.dto;

import java.util.UUID;

/**
 * FR-007/FR-012. Signature was locked to match Identity's (spec 002) then-temporary
 * {@code OrganizationAccountabilityStandIn.Answer} exactly (research.md §7), so
 * {@code ManagerScopeGuard} needed no change when it swapped that stand-in for
 * this real type (tasks.md T032).
 *
 * <p>{@code UNKNOWN_IDENTIFIER} is declared here but not reachable yet: Teacher/
 * School Master Data don't exist as validated master tables, so every syntactically
 * valid identifier resolves to {@code UNASSIGNED} at worst (research.md §3, a
 * deliberate, documented narrowing — not an oversight).
 */
public record AccountabilityAnswer(State state, UUID managerId) {

    public enum State {
        CURRENT_MANAGER,
        UNASSIGNED,
        UNKNOWN_IDENTIFIER
    }

    public static AccountabilityAnswer currentManager(UUID managerId) {
        return new AccountabilityAnswer(State.CURRENT_MANAGER, managerId);
    }

    public static AccountabilityAnswer unassigned() {
        return new AccountabilityAnswer(State.UNASSIGNED, null);
    }
}
