package com.hls.payroll.run;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.audit.support.AuditVisibility;
import com.hls.identity.user.Role;
import com.hls.support.IntegrationTestBase;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Spec 013b T009: every entity type this spec writes to the audit trail is mapped to a permission. A missing mapping hid
 * entries from everyone and was caught only in review in spec 032.
 */
class PayrollAuditMappingTest extends IntegrationTestBase {

    /** The entity types the payroll run writes (data-model.md, contracts/payroll-run-api.md). */
    static final List<String> PAYROLL_ENTITY_TYPES =
            List.of("PAYROLL_RUN", "PAYROLL_ADJUSTMENT", "PAYROLL_PAYMENT", "PAYROLL_EXPORT");

    @Autowired
    private AuditVisibility visibility;

    @Test
    void everyPayrollEntityTypeIsHiddenFromRolesWithoutPayrollViewAndShownToAdminAndDirector() {
        for (Role role : List.of(Role.MANAGER, Role.TEACHER, Role.SYSTEM)) {
            assertThat(visibility.hiddenEntityTypes(Set.of(role)))
                    .as(role.name())
                    .containsAll(PAYROLL_ENTITY_TYPES);
        }
        for (Role role : List.of(Role.ADMIN, Role.DIRECTOR)) {
            assertThat(visibility.hiddenEntityTypes(Set.of(role)))
                    .as(role.name())
                    .doesNotContainAnyElementsOf(PAYROLL_ENTITY_TYPES);
        }
    }
}
