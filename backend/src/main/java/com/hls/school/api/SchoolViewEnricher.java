package com.hls.school.api;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/** Lets dependent modules add attributes they own (Manager, Teacher count) to School views. */
public interface SchoolViewEnricher {

    Map<UUID, Map<String, Object>> enrich(Collection<UUID> schoolIds);
}
