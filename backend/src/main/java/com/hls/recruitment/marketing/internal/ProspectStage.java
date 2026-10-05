package com.hls.recruitment.marketing.internal;

import java.util.EnumSet;
import java.util.Set;

/**
 * The stored stages of a prospect. MoU and Active are not stored: they are derived from the School's contract in spec
 * 012, so nothing here can disagree with the contract. ON_HOLD and LOST remember the stage to return to.
 */
public enum ProspectStage {
    PROSPECT,
    CONTACTED,
    VISIT,
    FOLLOW_UP,
    INTERESTED,
    NEGOTIATION,
    FINAL_STAGE,
    ON_HOLD,
    LOST;

    /** The seven stages a prospect moves through, in order. */
    public static final Set<ProspectStage> ACTIVE =
            EnumSet.of(PROSPECT, CONTACTED, VISIT, FOLLOW_UP, INTERESTED, NEGOTIATION, FINAL_STAGE);

    public boolean isActive() {
        return ACTIVE.contains(this);
    }
}
