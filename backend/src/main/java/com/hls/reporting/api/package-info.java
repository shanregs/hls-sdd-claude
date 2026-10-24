/**
 * Public API of the {@code reporting} module (spec 028): the extension point the owning modules implement to put a figure on
 * the management Dashboard ({@link com.hls.reporting.api.TileSource}, {@link com.hls.reporting.api.ApprovalSource},
 * {@link com.hls.reporting.api.ManagerColumns}). Depends only on {@code identity} types; no source module is depended on by
 * {@code reporting}.
 *
 * <p>Adding a tile later: implement {@link com.hls.reporting.api.TileSource} in the owning module's {@code internal} package
 * as a {@code @Component} (constant {@link com.hls.reporting.api.TileDefinition}s, a {@code read} that stays within the
 * caller's scope and never returns 0 for a missing figure), add its row to {@code contracts/tile-catalog.md} in the spec and a
 * parity test beside the module's tests. Nothing in {@code reporting} changes. See quickstart section 7 of spec 028.
 */
@org.springframework.modulith.NamedInterface
package com.hls.reporting.api;
