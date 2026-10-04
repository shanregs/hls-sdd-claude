package com.hls.identity.permissions;

import com.hls.identity.user.Role;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Request/response shapes for {@code contracts/access-model-api.md}'s permission-matrix endpoints. */
public final class PermissionMatrixDtos {

    private PermissionMatrixDtos() {}

    public record EntryView(Role role, PermissionModule module, PermissionAction action, boolean granted) {
        static EntryView from(PermissionMatrixEntry entry) {
            return new EntryView(entry.getRole(), entry.getModule(), entry.getAction(), entry.isGranted());
        }
    }

    /** One module and, per role, the actions that can be granted (an empty list means "not applicable"). */
    public record ModuleView(PermissionModule module, Map<Role, List<PermissionAction>> eligible) {
        static ModuleView of(PermissionModule module) {
            Map<Role, List<PermissionAction>> eligible = new EnumMap<>(Role.class);
            for (Role role : Role.values()) {
                eligible.put(role, List.copyOf(PermissionEligibility.actionsFor(role, module)));
            }
            return new ModuleView(module, eligible);
        }
    }

    /** The stored grants plus the module/role/action grid that says which of them can exist. */
    public record MatrixListResponse(List<EntryView> entries, List<ModuleView> modules) {
        static MatrixListResponse of(List<EntryView> entries) {
            return new MatrixListResponse(
                    entries, java.util.Arrays.stream(PermissionModule.values()).map(ModuleView::of).toList());
        }
    }

    public record GrantRequest(boolean granted) {}

    public record RejectionResponse(String reason) {}
}
