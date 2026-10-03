package com.hls.school.api;

import java.util.UUID;

/** A Place with the Zone it belongs to. */
public record PlaceView(UUID id, String name, String pinCode, UUID zoneId, String zoneName) {}
