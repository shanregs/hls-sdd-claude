package com.hls.teacher;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.modulith.test.ApplicationModuleTest.BootstrapMode;
import org.springframework.test.context.TestPropertySource;

/**
 * Spring Modulith isolation test, same pattern as {@code IdentityModuleTest}.
 * {@code ALL_DEPENDENCIES}, not {@code DIRECT_DEPENDENCIES} as research.md §6
 * originally planned — discovered during implementation: {@code teacher}'s
 * direct dependency on {@code identity.api} pulls in {@code identity}'s own
 * beans (e.g. {@code ManagerScopeGuard}), which themselves have a real bean
 * dependency on {@code organization.api} (a transitive, not direct,
 * dependency of {@code teacher}). {@code DIRECT_DEPENDENCIES} only bootstraps
 * immediate dependencies, so it failed to start with a missing
 * {@code AccountabilityQueries} bean; {@code ALL_DEPENDENCIES} bootstraps the
 * full transitive graph instead.
 */
@ApplicationModuleTest(BootstrapMode.ALL_DEPENDENCIES)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:teacher-module-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class TeacherModuleTest {

    @Test
    void moduleBootsInIsolation() {
        // Intentionally empty: @ApplicationModuleTest's context load is the assertion.
    }
}
