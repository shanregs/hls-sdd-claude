package com.hls.training.api;

import java.time.LocalDate;
import java.util.UUID;

/** An induction batch by identifier, for specs that link to one (Training Stay expenses in spec 015). */
public record InductionBatchView(UUID id, String name, LocalDate startsOn, LocalDate endsOn) {}
