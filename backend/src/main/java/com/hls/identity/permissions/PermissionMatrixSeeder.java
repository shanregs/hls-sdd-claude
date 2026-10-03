package com.hls.identity.permissions;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** Seeds the default permission matrix on every startup (FR-001) — idempotent, unlike
 * spec 001's dev-only demo-data seeder, this always runs since the matrix is required for the
 * access model to resolve anything. */
@Component
public class PermissionMatrixSeeder implements ApplicationRunner {

    private final PermissionMatrixService permissionMatrixService;

    public PermissionMatrixSeeder(PermissionMatrixService permissionMatrixService) {
        this.permissionMatrixService = permissionMatrixService;
    }

    @Override
    public void run(ApplicationArguments args) {
        permissionMatrixService.seedDefaults();
    }
}
