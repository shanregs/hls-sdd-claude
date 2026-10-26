package com.hls.content.api;

import java.time.LocalDate;
import java.util.UUID;

/** Content was created for an author other than the person who created it (spec 029). The author is told. */
public record ContentAssigned(UUID contentId, String title, UUID authorUserId, UUID assignedByUserId, LocalDate dueOn) {}
