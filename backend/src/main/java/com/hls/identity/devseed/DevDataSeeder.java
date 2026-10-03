package com.hls.identity.devseed;

import com.hls.identity.user.AppUserRepository;
import com.hls.identity.user.Role;
import com.hls.identity.user.UserAdminService;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Dev-only convenience for manually exercising every login method from a real database (not
 * Testcontainers). Not part of any spec's requirements — inert unless {@code
 * hls.seed.demo-data=true} is set, and idempotent (skips users that already exist by phone).
 */
@Component
@ConditionalOnProperty(name = "hls.seed.demo-data", havingValue = "true")
public class DevDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevDataSeeder.class);

    private final UserAdminService userAdminService;
    private final AppUserRepository appUserRepository;

    public DevDataSeeder(UserAdminService userAdminService, AppUserRepository appUserRepository) {
        this.userAdminService = userAdminService;
        this.appUserRepository = appUserRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        seed("Asha Admin", "9800000001", Set.of(Role.ADMIN), "Password123!", "asha.admin", "asha.admin@example.com");
        seed("Divya Director", "9800000002", Set.of(Role.DIRECTOR), "Password123!", null, null);
        seed(
                "Manoj Manager",
                "9800000003",
                Set.of(Role.MANAGER),
                "Password123!",
                "manoj.manager",
                "manoj.manager@example.com");
        seed("Tara Teacher", "9800000004", Set.of(Role.TEACHER), null, null, null);
        seed("Sunil System", "9800000005", Set.of(Role.SYSTEM), "Password123!", "sunil.system", null);

        log.info(
                "[DEV SEED] Demo users ready. Try: 9800000001/Password123! (Admin, phone+password), "
                        + "asha.admin/Password123! (Admin, username+password, has email for reset-by-email), "
                        + "9800000002/Password123! (Director), 9800000003 or manoj.manager/Password123! (Manager, "
                        + "has email), 9800000004 (Teacher, OTP-only sign-in, no password), 9800000005 or "
                        + "sunil.system/Password123! (System).");
    }

    private void seed(String name, String phone, Set<Role> roles, String password, String username, String email) {
        if (appUserRepository.findByPhone(phone).isPresent()) {
            return;
        }
        userAdminService.createUser(name, phone, roles, null, password, username, email);
    }
}
