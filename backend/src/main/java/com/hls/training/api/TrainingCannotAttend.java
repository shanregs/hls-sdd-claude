package com.hls.training.api;

import java.util.UUID;

/** An assigned person said they cannot attend; Admin and Director hear of it, nothing else changes (spec 024). */
public record TrainingCannotAttend(UUID sessionId, String personName, String note) {}
