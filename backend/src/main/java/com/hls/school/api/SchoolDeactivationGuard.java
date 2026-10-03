package com.hls.school.api;

import java.util.UUID;

/** Implemented by dependent modules to veto deactivating a School; throw {@link ConflictException}. */
public interface SchoolDeactivationGuard {

    void checkDeactivate(UUID schoolId);
}
