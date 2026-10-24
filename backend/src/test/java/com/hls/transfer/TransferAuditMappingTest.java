package com.hls.transfer;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.audit.support.AuditVisibility;
import com.hls.identity.user.Role;
import com.hls.support.IntegrationTestBase;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Spec 027 T008: every entity type this spec writes to the audit trail is mapped to a permission. */
class TransferAuditMappingTest extends IntegrationTestBase {

    static final List<String> TRANSFER_ENTITY_TYPES = List.of("MOVEMENT_REQUEST");

    @Autowired
    private AuditVisibility visibility;

    @Test
    void movementRequestIsHiddenFromTeacherAndSystemAndShownToStaff() {
        for (Role role : List.of(Role.TEACHER, Role.SYSTEM)) {
            assertThat(visibility.hiddenEntityTypes(Set.of(role)))
                    .as(role.name())
                    .containsAll(TRANSFER_ENTITY_TYPES);
        }
        for (Role role : List.of(Role.ADMIN, Role.DIRECTOR, Role.MANAGER)) {
            assertThat(visibility.hiddenEntityTypes(Set.of(role)))
                    .as(role.name())
                    .doesNotContainAnyElementsOf(TRANSFER_ENTITY_TYPES);
        }
    }
}
