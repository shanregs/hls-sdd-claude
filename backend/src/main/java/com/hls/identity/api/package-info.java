/**
 * Public surface of the {@code identity} module. Only types in this package
 * may be depended on by other modules — no {@code dto} subpackage exists here
 * yet ({@code ManagerScopeQueries}/{@code TeacherScopeQueries} return plain
 * {@code boolean}); if one is added later, it needs its own
 * {@code package-info.java} carrying this same {@code "api"} named-interface
 * name (propagation only reaches types in the *same* package as the
 * annotated one, not a nested subpackage).
 *
 * <p>{@code @NamedInterface} tells Spring Modulith this package (Spring
 * Modulith's package-based convention otherwise treats every subpackage of a
 * module as internal by default) is the module's open, exposed surface.
 */
@org.springframework.modulith.NamedInterface("api")
package com.hls.identity.api;
