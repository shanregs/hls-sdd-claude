package com.hls.audit.api.dto;

/** FR-002: every record-affecting change this module records. */
public enum AuditAction {
    CREATED,
    UPDATED,
    CORRECTED
}
