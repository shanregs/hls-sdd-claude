package com.hls.reporting.api;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Columns a module contributes to the Manager-by-Manager table (spec 028 US2). */
public interface ManagerColumns {

    /** {@code linkTemplate} may contain {@code {managerId}}. */
    record Column(String key, String label, String linkTemplate) {}

    List<Column> columns();

    /** Bulk: figure per column key per Manager user id, in a fixed number of queries. */
    Map<UUID, Map<String, Long>> figures(TileContext ctx, Set<UUID> managerUserIds);
}
