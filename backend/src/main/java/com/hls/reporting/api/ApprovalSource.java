package com.hls.reporting.api;

import com.hls.identity.permissions.PermissionAction;
import com.hls.identity.user.Role;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** The decisions a module has waiting; the inbox merges them (spec 028 US6). */
public interface ApprovalSource {

    /** For example {@code MANAGER_LEAVE}, {@code FORGOTTEN_CHECK_IN}, {@code FINAL_STAGE_PROSPECT}. */
    String kind();

    /** A {@link com.hls.identity.permissions.PermissionModule} constant name. */
    String permissionModule();

    /** Approve or Process on {@link #permissionModule()}. */
    PermissionAction action();

    /** Items the caller may decide; reporting sorts, drops the caller's own requests and pages. */
    List<ApprovalItem> pending(UUID userId, Set<Role> roles);
}
