package com.hls.content.api;

import java.util.UUID;

/** A version waits for review but nobody other than its author and submitter can approve it (spec 029). Admins and Directors are told once. */
public record ContentNoReviewer(UUID contentId, String title, int versionNumber) {}
