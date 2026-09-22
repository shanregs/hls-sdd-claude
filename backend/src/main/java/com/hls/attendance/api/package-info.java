/**
 * Public surface of the {@code attendance} module. Only types here (and its
 * {@code dto} subpackage, separately annotated with the same named-interface
 * name) may be depended on by other modules.
 *
 * <p>{@code @NamedInterface} tells Spring Modulith this package is the
 * module's open, exposed surface. {@code propagate} only reaches types in the
 * <em>same</em> package as the annotated one, not a nested subpackage, so
 * {@code api.dto} needs its own {@code package-info.java} carrying the
 * identical {@code "api"} name (established pattern, e.g. {@code teacher}).
 */
@org.springframework.modulith.NamedInterface("api")
package com.hls.attendance.api;
