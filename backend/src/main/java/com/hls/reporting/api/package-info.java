/**
 * Public API of the {@code reporting} module (spec 028): the extension point the owning modules implement to put a figure on
 * the management Dashboard ({@link com.hls.reporting.api.TileSource}, {@link com.hls.reporting.api.ApprovalSource},
 * {@link com.hls.reporting.api.ManagerColumns}). Depends only on {@code identity} types; no source module is depended on by
 * {@code reporting}.
 */
@org.springframework.modulith.NamedInterface
package com.hls.reporting.api;
