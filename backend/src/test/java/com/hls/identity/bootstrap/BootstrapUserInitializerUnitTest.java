package com.hls.identity.bootstrap;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.hls.identity.user.UserAdminService;
import org.junit.jupiter.api.Test;

/**
 * User Story 6, Acceptance Scenario 3 (spec 001-identity-access): missing bootstrap configuration
 * is reported clearly (a log warning) with no invented default credentials, rather than an
 * exception or a fallback password (FR-020). Tested directly against
 * {@link BootstrapUserInitializer}, without a Spring context, since the behavior under test is
 * pure "was createUser ever called".
 */
class BootstrapUserInitializerUnitTest {

    @Test
    void blankBootstrapConfigurationCreatesNoUsers() {
        UserAdminService mockUserAdminService = mock(UserAdminService.class);
        BootstrapUserInitializer initializer =
                new BootstrapUserInitializer(mockUserAdminService, "", "", "", "", "", "");

        initializer.run(null);

        verify(mockUserAdminService, never()).createUser(anyString(), anyString(), anySet(), any(), anyString());
    }
}
