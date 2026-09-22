package com.hls.school;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.test.context.TestPropertySource;

/**
 * Spring Modulith isolation test, same pattern as {@code AuditModuleTest}: H2
 * in-memory, Flyway disabled, Hibernate auto-DDL — structural check that
 * {@code school} boots on its own, not a persistence test. Default
 * {@code BootstrapMode.STANDALONE} is expected to be sufficient since
 * {@code school} depends on nothing from any other module and nothing yet
 * depends on {@code school} either (research.md §3) — revisit once
 * specs/006-zone-scoping adds a real dependency on {@code school.api}.
 */
@ApplicationModuleTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:school-module-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class SchoolModuleTest {

    @Test
    void moduleBootsInIsolation() {
        // Intentionally empty: @ApplicationModuleTest's context load is the assertion.
    }
}
