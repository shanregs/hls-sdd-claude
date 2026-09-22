package com.hls.audit;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.test.context.TestPropertySource;

/**
 * Spring Modulith isolation test, same pattern as {@code IdentityModuleTest}/
 * {@code OrganizationModuleTest}: H2 in-memory, Flyway disabled, Hibernate
 * auto-DDL — structural check that {@code audit} boots on its own, not a
 * persistence test. Default {@code BootstrapMode.STANDALONE} is expected to
 * be sufficient since {@code audit} depends on nothing from any other module
 * and nothing yet depends on {@code audit} either (research.md §2) — revisit
 * only if a real caller module later adds a dependency, the same way
 * Organization's tasks.md T031 had to switch Identity's test to
 * {@code DIRECT_DEPENDENCIES}.
 */
@ApplicationModuleTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:audit-module-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class AuditModuleTest {

    @Test
    void moduleBootsInIsolation() {
        // Intentionally empty: @ApplicationModuleTest's context load is the assertion.
    }
}
