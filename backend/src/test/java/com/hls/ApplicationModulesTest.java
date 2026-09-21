package com.hls;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/**
 * Whole-application Spring Modulith verification (Constitution Principle V:
 * "ArchUnit and Spring Modulith verification"). Unlike {@code IdentityModuleTest}/
 * {@code OrganizationModuleTest} (each boots one module in isolation), this checks
 * the *entire* module graph — including dependency direction — across every
 * module Spring Modulith detects (currently {@code status}, {@code identity},
 * {@code organization}).
 *
 * <p>Found missing during Organization's implementation (2026-09-22): both
 * modules' plan.md documents claimed to introduce this, but only the per-module
 * isolation tests were ever written. This is exactly the check that would catch
 * a module dependency cycle — e.g. if {@code organization} were ever changed to
 * depend back on {@code identity}, which would contradict the one-directional
 * dependency plan.md's Constitution Check asserts.
 */
class ApplicationModulesTest {

    @Test
    void moduleGraphHasNoBoundaryViolations() {
        ApplicationModules modules = ApplicationModules.of(HlsApplication.class);
        modules.verify();
    }
}
