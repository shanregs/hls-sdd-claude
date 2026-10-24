package com.hls.training.api;

import java.time.LocalDate;
import java.util.UUID;

/** A reminder is due for one person, {@code daysBefore} days before the session (spec 024). */
public record TrainingReminderDue(UUID sessionId, UUID userId, int daysBefore, LocalDate date, String topic) {}
