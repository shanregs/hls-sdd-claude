package com.hls.school.api;

/** FR-005/FR-006: thrown when a batch itself is rejected (empty, or over the max row count) — before any row is processed. */
public class BulkImportBatchException extends RuntimeException {

    public BulkImportBatchException(String message) {
        super(message);
    }
}
