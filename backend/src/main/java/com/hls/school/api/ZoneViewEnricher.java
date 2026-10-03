package com.hls.school.api;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/** Lets dependent modules add attributes they own (Manager count) to Zone views. */
public interface ZoneViewEnricher {

    Map<UUID, Map<String, Object>> enrich(Collection<UUID> zoneIds);
}
