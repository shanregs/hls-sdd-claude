package com.hls.identity.permissions;

import com.hls.identity.user.Role;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PermissionMatrixRepository extends JpaRepository<PermissionMatrixEntry, UUID> {

    Optional<PermissionMatrixEntry> findByRoleAndModuleAndAction(Role role, PermissionModule module, PermissionAction action);
}
