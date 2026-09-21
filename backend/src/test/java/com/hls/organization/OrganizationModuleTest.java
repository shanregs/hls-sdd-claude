package com.hls.organization;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.test.context.TestPropertySource;

/**
 * Spring Modulith isolation test, same pattern as {@code IdentityModuleTest}
 * (spec 002): H2 in-memory, Flyway disabled, Hibernate auto-DDL — structural
 * check that {@code organization} boots on its own, not a persistence test.
 */
@ApplicationModuleTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:organization-module-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class OrganizationModuleTest {

    @Test
    void moduleBootsInIsolation() {
        // Intentionally empty: @ApplicationModuleTest's context load is the assertion.
    }
}
