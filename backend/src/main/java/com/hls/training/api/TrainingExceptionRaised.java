package com.hls.training.api;

import java.time.LocalDate;
import java.util.UUID;

/** A new non-compliance exception was raised (an assigned person was recorded Absent); Admin and Director hear of it. */
public record TrainingExceptionRaised(UUID sessionId, String personName, LocalDate date) {}
