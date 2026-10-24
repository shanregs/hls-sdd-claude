package com.hls.reporting.api;

/** {@code ORG_WIDE_ONLY} tiles (comparison table, payroll, inbox) are never offered to a Manager-only caller. */
public enum ScopeKind {
    ALL,
    ORG_WIDE_ONLY
}
