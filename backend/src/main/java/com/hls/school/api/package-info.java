/**
 * Public surface of the {@code school} module. Only types here (and its
 * {@code dto} subpackage, separately annotated with the same named-interface
 * name) may be depended on by other modules.
 *
 * <p>{@code @NamedInterface} tells Spring Modulith this package is the
 * module's open, exposed surface. {@code propagate} only reaches types in the
 * <em>same</em> package as the annotated one, not a nested subpackage, so
 * {@code api.dto} needs its own {@code package-info.java} carrying the
 * identical {@code "api"} name (specs/003-organization-scoping tasks.md T031
 * finding 2 — applied here from the start rather than retrofitted).
 */
@org.springframework.modulith.NamedInterface("api")
package com.hls.school.api;
