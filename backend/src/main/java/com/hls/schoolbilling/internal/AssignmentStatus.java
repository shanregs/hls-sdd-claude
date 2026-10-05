package com.hls.schoolbilling.internal;

/** ACTIVE rows form a Teacher's assignment history; CANCELLED and CORRECTED rows are kept for audit. */
public enum AssignmentStatus {
    ACTIVE,
    CANCELLED,
    CORRECTED
}
