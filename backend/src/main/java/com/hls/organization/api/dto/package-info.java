/**
 * Value types for the {@code organization} module's public surface. Given the
 * same named-interface name ({@code "api"}) as {@code com.hls.organization.api}
 * itself, so Spring Modulith treats both packages as one exposed surface
 * rather than requiring every caller to also depend on this subpackage
 * separately.
 */
@org.springframework.modulith.NamedInterface("api")
package com.hls.organization.api.dto;
