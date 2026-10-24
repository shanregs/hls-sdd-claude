package com.hls.reporting.api;

import com.hls.identity.user.Role;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/** What a source needs to read one tile for one caller. The Zone, if any, is already checked against the caller's scope. */
public interface TileContext {

    UUID userId();

    Set<Role> roles();

    LocalDate today();

    YearMonth month();

    Optional<UUID> zoneId();

    /** Computes once per request and shares the value between the tiles of that request. */
    <T> T memo(String key, Supplier<T> compute);
}
