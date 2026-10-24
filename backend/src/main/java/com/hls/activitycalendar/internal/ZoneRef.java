package com.hls.activitycalendar.internal;

import java.util.UUID;

/** A Zone with its name; null where an item has no Zone ("No Zone" on screen). */
public record ZoneRef(UUID id, String name) {}
