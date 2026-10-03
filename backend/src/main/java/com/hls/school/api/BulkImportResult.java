package com.hls.school.api;

import java.util.List;

/** Per-row outcome of a Place bulk import (contracts/master-data-api.md). */
public record BulkImportResult(int added, int alreadyExisted, int rejected, List<RowResult> results) {

    public enum Outcome {
        ADDED,
        ALREADY_EXISTS,
        REJECTED
    }

    public record RowResult(int row, Outcome outcome, String reason) {}
}
