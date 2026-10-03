/**
 * Exposed to other modules (spec 003's {@code audit} consumes {@code PermissionGuard},
 * {@code PermissionModule}, {@code PermissionAction}, and {@code PermissionMatrixChanged}) —
 * Spring Modulith treats a sub-package as internal unless declared a named interface.
 */
@org.springframework.modulith.NamedInterface
package com.hls.identity.permissions;
