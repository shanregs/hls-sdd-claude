package com.hls.organization.internal;

import com.hls.identity.activity.AccountActivationChanged;
import com.hls.identity.activity.UserRoleChanged;
import com.hls.identity.user.Role;
import com.hls.identity.user.UserAdminService;
import java.time.Clock;
import java.util.UUID;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Keeps {@code manager.active} in step with the Manager's account (spec 004 events): a Manager is
 * active only while their user is active and holds the Manager role. Assignment rows and history
 * are never touched (spec 005 edge case on Manager role loss).
 */
@Component
public class ManagerAccountSync {

    private final ManagerRepository managerRepository;
    private final UserAdminService userAdminService;
    private final ManagerEmploymentService employment;
    private final Clock clock;

    public ManagerAccountSync(
            ManagerRepository managerRepository,
            UserAdminService userAdminService,
            ManagerEmploymentService employment,
            Clock clock) {
        this.managerRepository = managerRepository;
        this.userAdminService = userAdminService;
        this.employment = employment;
        this.clock = clock;
    }

    @ApplicationModuleListener
    void on(UserRoleChanged event) {
        if (event.role() == Role.MANAGER) {
            resync(event.affectedUserId(), event.actorUserId());
        }
    }

    @ApplicationModuleListener
    void on(AccountActivationChanged event) {
        resync(event.affectedUserId(), event.actorUserId());
    }

    private void resync(UUID userId, UUID actor) {
        managerRepository.findByUserId(userId).ifPresent(manager -> {
            boolean shouldBeActive = userAdminService
                    .find(userId)
                    .map(user -> user.user().isActive() && user.roles().contains(Role.MANAGER))
                    .orElse(false);
            if (manager.isActive() != shouldBeActive) {
                manager.setActive(shouldBeActive, clock.instant());
                managerRepository.save(manager);
                // spec 005a FR-008: inactive gets today's exit date when none, active again clears it
                employment.accountStateChanged(actor, manager);
            }
        });
    }
}
