/**
 * Exposed to other modules (spec 003's {@code audit} consumes {@code Role} to authorize its
 * endpoints) — Spring Modulith treats a sub-package as internal unless declared a named interface.
 * A finer-grained split (a dedicated public-API package for just {@code Role}) would be tighter,
 * but is out of scope for this spec.
 */
@org.springframework.modulith.NamedInterface
package com.hls.identity.user;
