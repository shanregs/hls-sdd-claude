/**
 * Exposed to other modules (spec 003's {@code audit} consumes {@code LoginHistoryRecorded},
 * {@code LoginMethod}, {@code LoginEventType}) — Spring Modulith treats a sub-package as internal
 * unless declared a named interface.
 */
@org.springframework.modulith.NamedInterface
package com.hls.identity.loginhistory;
