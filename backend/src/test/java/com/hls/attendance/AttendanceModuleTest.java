package com.hls.attendance;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.modulith.test.ApplicationModuleTest.BootstrapMode;
import org.springframework.test.context.TestPropertySource;

/**
 * Spring Modulith isolation test, same pattern as {@code TeacherModuleTest}.
 * {@code attendance} has real bean dependencies on {@code identity.api},
 * {@code audit.api}, {@code teacher.api}, and (User Story 5) {@code
 * organization.api} from day one, so {@code ALL_DEPENDENCIES} is used from
 * the start rather than discovering the need during implementation
 * (specs/005-teacher research.md §6's lesson, applied up front).
 */
@ApplicationModuleTest(BootstrapMode.ALL_DEPENDENCIES)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:attendance-module-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class AttendanceModuleTest {

    @Test
    void moduleBootsInIsolation() {
        // Intentionally empty: @ApplicationModuleTest's context load is the assertion.
    }
}
