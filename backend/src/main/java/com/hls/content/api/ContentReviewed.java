package com.hls.content.api;

import java.util.UUID;

/** A reviewer approved or returned a version (spec 029). {@code decision} is APPROVED or RETURNED. The author and submitter are told. */
public record ContentReviewed(
        UUID contentId,
        String title,
        int versionNumber,
        String decision,
        String comment,
        UUID authorUserId,
        UUID submittedByUserId,
        UUID reviewerUserId) {}
