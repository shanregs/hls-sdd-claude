package com.hls.school.api;

import java.util.UUID;

/** Implemented by dependent modules to veto deleting a Zone; throw {@link ConflictException} to refuse. */
public interface ZoneChangeGuard {

    void checkDelete(UUID zoneId);
}
