package com.hls.identity.accessmodel;

/**
 * The data-scope classification carried per module in the access model (Constitution Principle
 * III, data-model.md). Ordered least to most permissive so a multi-role user's widest scope can be
 * picked by comparing ordinals.
 */
public enum DataScope {
    NONE,
    OWN,
    ASSIGNED,
    ORG_WIDE
}
