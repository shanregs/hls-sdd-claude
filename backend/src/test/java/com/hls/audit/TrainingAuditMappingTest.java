package com.hls.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.audit.support.AuditVisibility;
import com.hls.identity.user.Role;
import com.hls.support.IntegrationTestBase;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Spec 024 T010: every entity type the training calendar writes to the audit trail is mapped to the TRAINING permission. */
class TrainingAuditMappingTest extends IntegrationTestBase {

    static final List<String> TRAINING_ENTITY_TYPES = List.of(
            "TRAINING_SESSION", "TRAINING_PATTERN", "TRAINING_ASSIGNMENT", "TRAINING_REGISTRATION", "TRAINING_ATTENDANCE",
            "TRAINING_EXCEPTION");

    @Autowired
    private AuditVisibility visibility;

    @Test
    void trainingEntriesAreHiddenFromTeacherAndSystemAndShownToStaff() {
        for (Role role : List.of(Role.TEACHER, Role.SYSTEM)) {
            assertThat(visibility.hiddenEntityTypes(Set.of(role))).as(role.name()).containsAll(TRAINING_ENTITY_TYPES);
        }
        for (Role role : List.of(Role.ADMIN, Role.DIRECTOR, Role.MANAGER)) {
            assertThat(visibility.hiddenEntityTypes(Set.of(role)))
                    .as(role.name())
                    .doesNotContainAnyElementsOf(TRAINING_ENTITY_TYPES);
        }
    }
}
