package com.hls.school.api;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.Map;
import java.util.UUID;

/** A Zone with its counts; {@code extras} carries attributes contributed by dependent modules. */
public record ZoneView(
        UUID id, String name, Long version, long placeCount, long schoolCount, @JsonIgnore Map<String, Object> extras) {

    @JsonAnyGetter
    public Map<String, Object> any() {
        return extras;
    }
}
