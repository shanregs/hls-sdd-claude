package com.hls.identity.permissions;

import com.hls.identity.user.Role;
import java.time.Instant;
import java.util.UUID;

/**
 * Published on every successful matrix edit (FR-003), for spec 003's Audit module to subscribe to
 * once it ships — {@code identity} does not depend on spec 003, and does not persist this itself
 * beyond the current matrix row (research.md's change-record note, spec 001's
 * {@code LoginHistoryRecorded} precedent). {@code eventId} lets Change History dedup redelivery
 * the same way Login History does (spec 003 research.md §1/§3).
 */
public record PermissionMatrixChanged(
        UUID eventId,
        UUID actorUserId,
        Instant occurredAt,
        Role role,
        PermissionModule module,
        PermissionAction action,
        boolean before,
        boolean after) {}
