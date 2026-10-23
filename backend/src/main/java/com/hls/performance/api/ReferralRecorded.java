package com.hls.performance.api;

import java.util.UUID;

/** A Teacher was referred for transfer or replacement (spec 026 FR-022); notification tells every active Director. */
public record ReferralRecorded(UUID issueId, UUID teacherId, String teacherName, String recommendation) {}
