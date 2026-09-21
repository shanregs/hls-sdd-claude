/**
 * Public surface of the {@code organization} module. Only types here (and its
 * {@code dto} subpackage, separately annotated with the same named-interface
 * name) may be depended on by other modules.
 *
 * <p>{@code @NamedInterface} tells Spring Modulith this package (Spring
 * Modulith's package-based convention otherwise treats every subpackage of a
 * module as internal by default) is the module's open, exposed surface.
 * {@code propagate} only reaches types in the *same* package as the annotated
 * one, not a nested subpackage, so {@code api.dto} needs its own
 * {@code package-info.java} carrying the identical {@code "api"} name.
 */
@org.springframework.modulith.NamedInterface("api")
package com.hls.organization.api;
