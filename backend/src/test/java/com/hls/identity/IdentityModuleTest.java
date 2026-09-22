package com.hls.identity;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.modulith.test.ApplicationModuleTest.BootstrapMode;
import org.springframework.test.context.TestPropertySource;

/**
 * Spring Modulith isolation test (plan.md, research.md §4): verifies the
 * {@code identity} module boots correctly given its declared module
 * dependencies. Deliberately structural, not a persistence test — uses an
 * in-memory H2 database with Hibernate auto-DDL instead of the real
 * Postgres/Flyway pairing (which {@code IdentityIntegrationTest} covers via
 * Testcontainers), so this check has no dependency on Docker being available.
 *
 * <p>{@code ALL_DEPENDENCIES} (rather than the default {@code STANDALONE}, and
 * no longer {@code DIRECT_DEPENDENCIES}): tasks.md T032 gave
 * {@code ManagerScopeGuard} a real Spring bean dependency on
 * {@code organization.api.AccountabilityQueries}, which was enough for
 * {@code DIRECT_DEPENDENCIES} at the time. specs/006-zone-scoping's rework
 * then gave {@code organization.internal.AccountabilityService} its own real
 * dependency on {@code school.api.ZoneQueries} — a *transitive* dependency of
 * {@code identity}, one level deeper than {@code DIRECT_DEPENDENCIES}
 * bootstraps. Same category of gap as specs/005-teacher's
 * {@code TeacherModuleTest} discovery (research.md §8 there); switched to
 * {@code ALL_DEPENDENCIES} here too rather than chasing the exact depth
 * needed each time a downstream module gains a new dependency.
 */
@ApplicationModuleTest(BootstrapMode.ALL_DEPENDENCIES)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:identity-module-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class IdentityModuleTest {

    @Test
    void moduleBootsInIsolation() {
        // Intentionally empty: @ApplicationModuleTest's context load is the assertion.
    }
}
