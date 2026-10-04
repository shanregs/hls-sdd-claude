/**
 * Exposed to other modules (spec 018's {@code audit} reads {@code ClientContext}, {@code
 * LocationCapture} and {@code ApiAccessRecorded} from events) — Spring Modulith treats a sub-package
 * as internal unless declared a named interface.
 */
@org.springframework.modulith.NamedInterface
package com.hls.identity.clientcontext;
