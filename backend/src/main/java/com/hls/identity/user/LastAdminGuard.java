package com.hls.identity.user;

import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * The one place the last-admin rule lives (spec 004 FR-007/FR-011, research.md section 3): a
 * change is refused if it would leave zero <em>active</em> users holding {@link Role#ADMIN}.
 *
 * <p>Callers MUST invoke this inside their own {@code @Transactional} method: the Admin
 * role-assignment rows are read under a pessimistic write lock, so two concurrent requests
 * serialize and cannot both pass the check.
 */
@Component
public class LastAdminGuard {

    public static final String REFUSAL_MESSAGE = "This would leave no active user able to administer the system as Admin.";

    private final RoleAssignmentRepository roleAssignmentRepository;
    private final AppUserRepository appUserRepository;

    public LastAdminGuard(RoleAssignmentRepository roleAssignmentRepository, AppUserRepository appUserRepository) {
        this.roleAssignmentRepository = roleAssignmentRepository;
        this.appUserRepository = appUserRepository;
    }

    /**
     * @param targetUserId the user being changed
     * @param rolesBeingRemoved roles about to be removed from them (empty for a plain deactivation
     *     check, in which case all of the target's roles stay but the account stops counting)
     * @param deactivating whether the target is about to be deactivated
     * @return true if the change would leave zero active Admins
     */
    public boolean wouldLeaveNoActiveAdmin(UUID targetUserId, Set<Role> rolesBeingRemoved, boolean deactivating) {
        var adminAssignments = roleAssignmentRepository.findByRole(Role.ADMIN);
        boolean targetLosesAdmin = deactivating || rolesBeingRemoved.contains(Role.ADMIN);
        long remainingActiveAdmins = appUserRepository
                .findAllById(adminAssignments.stream().map(RoleAssignment::getUserId).toList())
                .stream()
                .filter(AppUser::isActive)
                .filter(u -> !(targetLosesAdmin && u.getId().equals(targetUserId)))
                .count();
        boolean targetIsAnActiveAdmin = adminAssignments.stream().anyMatch(a -> a.getUserId().equals(targetUserId))
                && appUserRepository.findById(targetUserId).map(AppUser::isActive).orElse(false);
        // Only a change that actually removes an active Admin can break the invariant.
        return targetLosesAdmin && targetIsAnActiveAdmin && remainingActiveAdmins == 0;
    }
}
