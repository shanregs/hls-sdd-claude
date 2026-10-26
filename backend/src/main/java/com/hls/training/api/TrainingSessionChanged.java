package com.hls.training.api;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

/** A session was moved or cancelled; {@code change} is {@code MOVED} or {@code CANCELLED} (spec 024). */
public record TrainingSessionChanged(
        UUID sessionId, String change, Set<UUID> userIds, LocalDate oldDate, LocalDate newDate, String topic) {}
