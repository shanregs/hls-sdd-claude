package com.hls.expenses;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.audit.support.AuditVisibility;
import com.hls.identity.user.Role;
import com.hls.support.IntegrationTestBase;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Spec 015 T008: every entity type expenses audits is mapped to Expenses View, so a claimant sees none of them. */
class ExpenseAuditVisibilityTest extends IntegrationTestBase {

    static final List<String> EXPENSE_ENTITY_TYPES = List.of("EXPENSE_CLAIM", "EXPENSE_BUDGET", "EXPENSE_EXPORT");

    @Autowired
    private AuditVisibility visibility;

    @Test
    void expenseAuditEntriesAreHiddenFromEveryoneButAdminAndDirector() {
        for (Role role : List.of(Role.MANAGER, Role.TEACHER, Role.SYSTEM)) {
            assertThat(visibility.hiddenEntityTypes(Set.of(role)))
                    .as(role.name())
                    .containsAll(EXPENSE_ENTITY_TYPES);
        }
        for (Role role : List.of(Role.ADMIN, Role.DIRECTOR)) {
            assertThat(visibility.hiddenEntityTypes(Set.of(role)))
                    .as(role.name())
                    .doesNotContainAnyElementsOf(EXPENSE_ENTITY_TYPES);
        }
    }
}
