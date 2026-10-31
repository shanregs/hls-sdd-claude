package com.hls.incentive.api;

import java.util.UUID;

/**
 * The MoU of a School stopped counting as approved while its incentive sits in a pay run that awaits approval, so the
 * incentive could not be held back (spec 030 D-9). The Directors are told so they can return or approve the run knowingly.
 */
public record IncentiveMouChanged(UUID incentiveId, UUID schoolId, UUID earnerUserId) {}
