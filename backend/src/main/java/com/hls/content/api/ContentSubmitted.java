package com.hls.content.api;

import java.util.UUID;

/** A new version was submitted for review (spec 029). Everyone holding Approve on Content, except the author and submitter, is told. */
public record ContentSubmitted(UUID contentId, String title, int versionNumber, UUID authorUserId, UUID submittedByUserId) {}
