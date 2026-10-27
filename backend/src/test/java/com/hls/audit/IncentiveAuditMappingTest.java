package com.hls.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.audit.support.AuditVisibility;
import com.hls.identity.user.Role;
import com.hls.support.IntegrationTestBase;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Spec 030 T004: incentive history is mapped to the MANAGER_INCENTIVES permission. */
class IncentiveAuditMappingTest extends IntegrationTestBase {

    @Autowired
    private AuditVisibility visibility;

    @Test
    void incentiveEntriesAreHiddenFromTeacherAndSystemAndShownToStaff() {
        assertThat(visibility.hiddenEntityTypes(Set.of(Role.TEACHER))).contains("INCENTIVE");
        assertThat(visibility.hiddenEntityTypes(Set.of(Role.SYSTEM))).contains("INCENTIVE");
        for (Role role : new Role[] {Role.ADMIN, Role.DIRECTOR, Role.MANAGER}) {
            assertThat(visibility.hiddenEntityTypes(Set.of(role))).as(role.name()).doesNotContain("INCENTIVE");
        }
    }
}
