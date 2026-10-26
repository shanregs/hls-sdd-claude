package com.hls.training.api;

import java.time.LocalDate;
import java.util.UUID;

/** A Teacher's registration for an open session was confirmed (spec 024). */
public record TrainingRegistered(UUID sessionId, UUID userId, LocalDate date, String topic) {}
