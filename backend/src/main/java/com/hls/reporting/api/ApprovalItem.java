package com.hls.reporting.api;

import java.time.LocalDate;
import java.util.UUID;

/** One decision waiting for someone, as the owning module sees it. */
public record ApprovalItem(
        String kind, UUID itemId, UUID requesterUserId, String requesterName, String subject, LocalDate askedOn, Link link) {}
