package com.hls.organization.api;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/** Lets dependent modules add attributes they own (Teacher count) to Manager views. */
public interface ManagerViewEnricher {

    Map<UUID, Map<String, Object>> enrich(Collection<UUID> managerIds);
}
