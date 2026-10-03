package com.hls.school.api;

import com.hls.identity.user.Role;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Implemented by {@code organization}: the School ids a non-organization-wide caller may see.
 * {@code Optional.empty()} means "no opinion"; with no opinions at all the caller sees nothing
 * (fail closed).
 */
public interface SchoolScopeProvider {

    Optional<Set<UUID>> visibleSchoolIds(UUID userId, Set<Role> roles);
}
