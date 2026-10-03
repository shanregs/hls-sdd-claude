package com.hls.teacher.internal;

/** ACTIVE rows form the Teacher's placement history; CANCELLED and CORRECTED rows are kept for audit. */
public enum PlacementStatus {
    ACTIVE,
    CANCELLED,
    CORRECTED
}
