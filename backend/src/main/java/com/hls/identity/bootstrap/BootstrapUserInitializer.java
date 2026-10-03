package com.hls.identity.bootstrap;

import com.hls.identity.user.Role;
import com.hls.identity.user.UserAdminService;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Creates the first Admin and System users from deployment configuration on startup (FR-020,
 * research.md §8, User Story 6). Idempotent — skips a role whose bootstrap phone already exists,
 * and never overwrites an existing password. Reports clearly (a startup log warning, not a thrown
 * exception) rather than inventing default credentials when configuration is missing, since a
 * fresh deployment may deliberately defer bootstrap to a later step (spec.md edge case).
 */
@Component
public class BootstrapUserInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapUserInitializer.class);

    private final UserAdminService userAdminService;
    private final String adminDisplayName;
    private final String adminPhone;
    private final String adminPassword;
    private final String systemDisplayName;
    private final String systemPhone;
    private final String systemPassword;

    public BootstrapUserInitializer(
            UserAdminService userAdminService,
            @Value("${hls.bootstrap.admin.display-name:}") String adminDisplayName,
            @Value("${hls.bootstrap.admin.phone:}") String adminPhone,
            @Value("${hls.bootstrap.admin.password:}") String adminPassword,
            @Value("${hls.bootstrap.system.display-name:}") String systemDisplayName,
            @Value("${hls.bootstrap.system.phone:}") String systemPhone,
            @Value("${hls.bootstrap.system.password:}") String systemPassword) {
        this.userAdminService = userAdminService;
        this.adminDisplayName = adminDisplayName;
        this.adminPhone = adminPhone;
        this.adminPassword = adminPassword;
        this.systemDisplayName = systemDisplayName;
        this.systemPhone = systemPhone;
        this.systemPassword = systemPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        bootstrap("Admin", Role.ADMIN, adminDisplayName, adminPhone, adminPassword);
        bootstrap("System", Role.SYSTEM, systemDisplayName, systemPhone, systemPassword);
    }

    private void bootstrap(String label, Role role, String displayName, String phone, String password) {
        if (isBlank(displayName) || isBlank(phone) || isBlank(password)) {
            log.warn(
                    "No bootstrap {} configuration found (hls.bootstrap.{}.display-name/phone/password) "
                            + "- no initial {} user was created. Configure it and restart to create one.",
                    label,
                    label.toLowerCase(java.util.Locale.ROOT),
                    label);
            return;
        }
        try {
            userAdminService.createUser(displayName, phone, Set.of(role), null, password);
            log.info("Bootstrap {} user created.", label);
        } catch (UserAdminService.DuplicatePhoneException e) {
            log.debug("Bootstrap {} user already exists; skipping.", label);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
