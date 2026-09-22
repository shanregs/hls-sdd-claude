package com.hls.school.api.dto;

import java.util.List;

/** The overall response to a bulk-import submission (FR-003). */
public record BulkImportResponse(List<BulkImportRowResult> results, int successCount, int failureCount) {
}
