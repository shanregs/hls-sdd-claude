package com.hls.incentive.internal;

/** Why a School with an approved MoU earned nobody an incentive (spec 030 research D-2, D-3, D-4). */
public enum NoEarnerReason {
    NO_PROSPECT,
    NO_OWNER,
    OWNER_NOT_MANAGER,
    APPROVED_BEFORE_APPROVAL_STEP,
    BEFORE_GO_LIVE
}
