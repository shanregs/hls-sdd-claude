package com.hls.school.api;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.Map;
import java.util.UUID;

/**
 * A School with its Place and (derived) Zone; {@code extras} carries attributes contributed by
 * dependent modules ({@code manager}, {@code teacherCount}, {@code needsManager}).
 */
public record SchoolView(
        UUID id,
        String name,
        PlaceRef place,
        ZoneRef zone,
        String address,
        String contactPerson,
        String contactPhone,
        String billingContact,
        boolean active,
        Long version,
        @JsonIgnore Map<String, Object> extras) {

    public record PlaceRef(UUID id, String name, String pinCode) {}

    public record ZoneRef(UUID id, String name) {}

    @JsonAnyGetter
    public Map<String, Object> any() {
        return extras;
    }
}
