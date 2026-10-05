package com.hls.recruitment.api;

import java.util.UUID;

/** Published when a prospect's School is created or linked after its Final Stage review approved it. */
public record ProspectWon(UUID prospectId, UUID schoolId) {}
