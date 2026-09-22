package com.hls.school.api.dto;

/**
 * FR-003. The outcome of one row in a submitted batch, identified by its
 * position. {@code place} is set only when {@code succeeded}; {@code reason}
 * only when it isn't — never both.
 */
public record BulkImportRowResult(int index, boolean succeeded, PlaceView place, String reason) {

    public static BulkImportRowResult success(int index, PlaceView place) {
        return new BulkImportRowResult(index, true, place, null);
    }

    public static BulkImportRowResult failure(int index, String reason) {
        return new BulkImportRowResult(index, false, null, reason);
    }
}
