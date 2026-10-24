package com.hls.reporting.api;

import java.util.List;

/**
 * Implemented by the owning module in its own {@code internal} package. {@link #read} throwing, or taking longer than the
 * area reader's limit, shows that one tile as an error and nothing else.
 */
public interface TileSource {

    /** What this module offers; constant. */
    List<TileDefinition> tiles();

    /** Reads one tile for the caller, in a read-only transaction, within the caller's data scope. */
    TileResult read(TileDefinition tile, TileContext ctx);
}
