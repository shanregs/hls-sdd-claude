/**
 * Value types for the {@code attendance} module's public surface. Given the
 * same named-interface name ({@code "api"}) as {@code com.hls.attendance.api}
 * itself, so Spring Modulith treats both packages as one exposed surface
 * rather than requiring every caller to also depend on this subpackage
 * separately.
 */
@org.springframework.modulith.NamedInterface("api")
package com.hls.attendance.api.dto;
