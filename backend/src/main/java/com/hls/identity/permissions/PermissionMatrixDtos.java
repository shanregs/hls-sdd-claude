package com.hls.identity.permissions;

import com.hls.identity.user.Role;
import java.util.List;

/** Request/response shapes for {@code contracts/access-model-api.md}'s permission-matrix endpoints. */
public final class PermissionMatrixDtos {

    private PermissionMatrixDtos() {}

    public record EntryView(Role role, PermissionModule module, PermissionAction action, boolean granted) {
        static EntryView from(PermissionMatrixEntry entry) {
            return new EntryView(entry.getRole(), entry.getModule(), entry.getAction(), entry.isGranted());
        }
    }

    public record MatrixListResponse(List<EntryView> entries) {}

    public record GrantRequest(boolean granted) {}

    public record RejectionResponse(String reason) {}
}
