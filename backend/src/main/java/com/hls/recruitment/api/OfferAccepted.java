package com.hls.recruitment.api;

import java.util.UUID;

/**
 * Published when a job offer is accepted and its Teacher (in training) has been created. The training module listens
 * to enrol the Teacher in the next induction batch; recruitment never depends on training.
 */
public record OfferAccepted(UUID offerId, UUID candidateId, UUID teacherId) {}
