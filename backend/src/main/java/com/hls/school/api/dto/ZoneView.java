package com.hls.school.api.dto;

import java.util.UUID;

/** FR-002. The read-side shape of one Zone. */
public record ZoneView(UUID id, String name) {
}
