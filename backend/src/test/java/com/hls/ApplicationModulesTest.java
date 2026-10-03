package com.hls;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/**
 * Whole-application Spring Modulith verification (Constitution Principle V):
 * checks the entire module graph, including dependency direction, across every
 * module Spring Modulith detects under {@code com.hls}.
 */
class ApplicationModulesTest {

    @Test
    void moduleGraphHasNoBoundaryViolations() {
        ApplicationModules modules = ApplicationModules.of(HlsApplication.class);
        modules.verify();
    }
}
