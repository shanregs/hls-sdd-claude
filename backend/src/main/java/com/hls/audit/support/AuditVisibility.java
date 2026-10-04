package com.hls.audit.support;

import com.hls.identity.permissions.PermissionAction;
import com.hls.identity.permissions.PermissionMatrixService;
import com.hls.identity.permissions.PermissionModule;
import com.hls.identity.user.Role;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Decides which Change History entity types a caller may see (spec 005 research.md section 16):
 * each master-data entity type needs the matching module's VIEW grant, so System never sees school
 * or teacher data and nobody without salary access sees salary. Unlisted types (for example the
 * permission matrix) are unrestricted.
 */
@Component
public class AuditVisibility {

    private static final Map<String, PermissionModule> REQUIRED_VIEW = Map.ofEntries(
            Map.entry("ZONE", PermissionModule.ZONES),
            Map.entry("PLACE", PermissionModule.ZONES),
            Map.entry("SCHOOL", PermissionModule.SCHOOLS),
            Map.entry("MANAGER", PermissionModule.MANAGERS),
            Map.entry("ZONE_MANAGER_ASSIGNMENT", PermissionModule.MANAGERS),
            Map.entry("SCHOOL_MANAGER_ASSIGNMENT", PermissionModule.MANAGERS),
            Map.entry("TEACHER", PermissionModule.TEACHERS),
            Map.entry("TEACHER_PLACEMENT", PermissionModule.TEACHERS),
            Map.entry("TEACHER_SALARY", PermissionModule.TEACHER_SALARY),
            Map.entry("ATTENDANCE_MARK", PermissionModule.ATTENDANCE),
            Map.entry("ATTENDANCE_MONTH", PermissionModule.ATTENDANCE),
            Map.entry("ATTENDANCE_EXPORT", PermissionModule.ATTENDANCE),
            Map.entry("ATTENDANCE_CODE", PermissionModule.ATTENDANCE_SETUP),
            Map.entry("ATTENDANCE_CALENDAR", PermissionModule.ATTENDANCE_SETUP),
            Map.entry("LEAVE_REQUEST", PermissionModule.LEAVE_MANAGEMENT));

    private final PermissionMatrixService permissionMatrixService;

    public AuditVisibility(PermissionMatrixService permissionMatrixService) {
        this.permissionMatrixService = permissionMatrixService;
    }

    /** Entity types the caller's roles do <em>not</em> grant VIEW for. */
    public Set<String> hiddenEntityTypes(Set<Role> callerRoles) {
        Set<String> hidden = new HashSet<>();
        REQUIRED_VIEW.forEach((entityType, module) -> {
            boolean allowed = callerRoles.stream()
                    .anyMatch(role -> permissionMatrixService.isGranted(role, module, PermissionAction.VIEW));
            if (!allowed) {
                hidden.add(entityType);
            }
        });
        return hidden;
    }
}
